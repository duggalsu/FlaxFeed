/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package com.duggalsu.flaxfeed.ui.entries

import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.duggalsu.flaxfeed.R
import com.duggalsu.flaxfeed.databinding.FragmentEntriesBinding
import com.duggalsu.flaxfeed.databinding.ViewEntryBinding
import com.duggalsu.flaxfeed.data.entities.EntryWithFeed
import com.duggalsu.flaxfeed.data.entities.Feed
import com.duggalsu.flaxfeed.data.utils.PrefConstants
import com.duggalsu.flaxfeed.service.FetcherService
import com.duggalsu.flaxfeed.ui.main.MainActivity
import com.duggalsu.flaxfeed.ui.main.MainNavigator
import com.duggalsu.flaxfeed.utils.*
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.titleResource
import org.jetbrains.anko.sdk21.listeners.onClick
import org.jetbrains.anko.support.v4.dip
import org.jetbrains.anko.support.v4.share
import q.rorbin.badgeview.Badge
import q.rorbin.badgeview.QBadgeView
import java.util.*


class EntriesFragment : Fragment() {

    private var _binding: FragmentEntriesBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _binding_view_entry: ViewEntryBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding_view_entry get() = _binding_view_entry!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        _binding_view_entry = ViewEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _binding_view_entry = null
    }

    companion object {

        private const val ARG_FEED = "ARG_FEED"
        private const val STATE_FEED = "STATE_FEED"
        private const val STATE_SEARCH_TEXT = "STATE_SEARCH_TEXT"
        private const val STATE_SELECTED_ENTRY_ID = "STATE_SELECTED_ENTRY_ID"
        private const val STATE_LIST_DISPLAY_DATE = "STATE_LIST_DISPLAY_DATE"

        fun newInstance(feed: Feed?): EntriesFragment {
            return EntriesFragment().apply {
                feed?.let {
                    arguments = bundleOf(ARG_FEED to feed)
                }
            }
        }
    }

    var feed: Feed? = null
        set(value) {
            field = value

            setupTitle()
            binding.bottomNavigation.post { initDataObservers() } // Needed to retrieve the correct selected tab position
        }

    private val navigator: MainNavigator by lazy { activity as MainNavigator }

    private val adapter = EntryAdapter(
            displayThumbnails = context?.getPrefBoolean(PrefConstants.DISPLAY_THUMBNAILS, true) == true,
            globalClickListener = { entryWithFeed ->
                navigator.goToEntryDetails(entryWithFeed.entry.id, entryIds!!)
            },
            globalLongClickListener = { entryWithFeed ->
                share(entryWithFeed.entry.link.orEmpty(), entryWithFeed.entry.title.orEmpty())
            },
            favoriteClickListener = { entryWithFeed, view ->
                entryWithFeed.entry.favorite = !entryWithFeed.entry.favorite

                _binding_view_entry?.favoriteIcon?.let {
                    if (entryWithFeed.entry.favorite) {
                        it.setImageResource(R.drawable.ic_star_24dp)
                    } else {
                        it.setImageResource(R.drawable.ic_star_border_24dp)
                    }
                }

                doAsync {
                    if (entryWithFeed.entry.favorite) {
                        com.duggalsu.flaxfeed.App.db.entryDao().markAsFavorite(entryWithFeed.entry.id)
                    } else {
                        com.duggalsu.flaxfeed.App.db.entryDao().markAsNotFavorite(entryWithFeed.entry.id)
                    }
                }
            }
    )
    private var listDisplayDate = Date().time
    private var entriesLiveData: LiveData<PagedList<EntryWithFeed>>? = null
    private var entryIdsLiveData: LiveData<List<String>>? = null
    private var entryIds: List<String>? = null
    private var newCountLiveData: LiveData<Long>? = null
    private var unreadBadge: Badge? = null
    private var searchText: String? = null
    private val searchHandler = Handler()
    private var isDesc: Boolean = true
    private var fabScrollListener: RecyclerView.OnScrollListener? = null

    private val prefListener = OnSharedPreferenceChangeListener { _, key ->
        if (PrefConstants.IS_REFRESHING == key) {
            refreshSwipeProgress()
        }
    }

    init {
        setHasOptionsMenu(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            feed = savedInstanceState.getParcelable(STATE_FEED)
            adapter.selectedEntryId = savedInstanceState.getString(STATE_SELECTED_ENTRY_ID)
            listDisplayDate = savedInstanceState.getLong(STATE_LIST_DISPLAY_DATE)
            searchText = savedInstanceState.getString(STATE_SEARCH_TEXT)
        } else {
            feed = arguments?.getParcelable(ARG_FEED)
        }

        setupRecyclerView()

        binding.bottomNavigation.setOnNavigationItemSelectedListener {
            binding.recyclerView.post {
                listDisplayDate = Date().time
                initDataObservers()
                binding.recyclerView.scrollToPosition(0)
            }

            binding.toolbar.menu?.findItem(R.id.menu_entries__share)?.isVisible = it.itemId == R.id.favorites
            true
        }

        (activity as MainActivity).setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu_24dp)
        binding.toolbar.setNavigationContentDescription(R.string.navigation_button_content_description)
        binding.toolbar.setNavigationOnClickListener { (activity as MainActivity).toggleDrawer() }

        unreadBadge = QBadgeView(context).bindTarget((binding.bottomNavigation.getChildAt(0) as ViewGroup).getChildAt(0)).apply {
            setGravityOffset(35F, 0F, true)
            isShowShadow = false
            badgeBackgroundColor = requireContext().colorAttr(R.attr.colorUnreadBadgeBackground)
            badgeTextColor = requireContext().colorAttr(R.attr.colorUnreadBadgeText)
        }

        binding.readAllFab.onClick { _ ->
            entryIds?.let { entryIds ->
                if (entryIds.isNotEmpty()) {
                    doAsync {
                        // TODO check if limit still needed
                        entryIds.withIndex().groupBy { it.index / 300 }.map { pair -> pair.value.map { it.value } }.forEach {
                            com.duggalsu.flaxfeed.App.db.entryDao().markAsRead(it)
                        }
                    }

                    Snackbar
                            .make(binding.coordinator, R.string.marked_as_read, Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo) { _ ->
                                doAsync {
                                    // TODO check if limit still needed
                                    entryIds.withIndex().groupBy { it.index / 300 }.map { pair -> pair.value.map { it.value } }.forEach {
                                        com.duggalsu.flaxfeed.App.db.entryDao().markAsUnread(it)
                                    }

                                    uiThread {
                                        // we need to wait for the list to be empty before displaying the new items (to avoid scrolling issues)
                                        listDisplayDate = Date().time
                                        initDataObservers()
                                    }
                                }
                            }
                            .apply {
                                view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                    anchorId = R.id.bottom_navigation
                                    anchorGravity = Gravity.TOP
                                    gravity = Gravity.TOP
                                    insetEdge = Gravity.BOTTOM
                                }
                                show()
                            }
                }

                if (feed == null || feed?.id == Feed.ALL_ENTRIES_ID) {
                    activity?.notificationManager?.cancel(0)
                }
            }
        }
    }

    private fun initDataObservers() {
        isDesc = context?.getPrefBoolean(PrefConstants.SORT_ORDER, true)!!
        entryIdsLiveData?.removeObservers(viewLifecycleOwner)
        entryIdsLiveData = when {
            searchText != null -> com.duggalsu.flaxfeed.App.db.entryDao().observeIdsBySearch(searchText!!, isDesc)
            feed?.isGroup == true && binding.bottomNavigation.selectedItemId == R.id.unreads -> com.duggalsu.flaxfeed.App.db.entryDao().observeUnreadIdsByGroup(feed!!.id, listDisplayDate, isDesc)
            feed?.isGroup == true && binding.bottomNavigation.selectedItemId == R.id.favorites -> com.duggalsu.flaxfeed.App.db.entryDao().observeFavoriteIdsByGroup(feed!!.id, listDisplayDate, isDesc)
            feed?.isGroup == true -> com.duggalsu.flaxfeed.App.db.entryDao().observeIdsByGroup(feed!!.id, listDisplayDate, isDesc)

            feed != null && feed?.id != Feed.ALL_ENTRIES_ID && binding.bottomNavigation.selectedItemId == R.id.unreads -> com.duggalsu.flaxfeed.App.db.entryDao().observeUnreadIdsByFeed(feed!!.id, listDisplayDate, isDesc)
            feed != null && feed?.id != Feed.ALL_ENTRIES_ID && binding.bottomNavigation.selectedItemId == R.id.favorites -> com.duggalsu.flaxfeed.App.db.entryDao().observeFavoriteIdsByFeed(feed!!.id, listDisplayDate, isDesc)
            feed != null && feed?.id != Feed.ALL_ENTRIES_ID -> com.duggalsu.flaxfeed.App.db.entryDao().observeIdsByFeed(feed!!.id, listDisplayDate, isDesc)

            binding.bottomNavigation.selectedItemId == R.id.unreads -> com.duggalsu.flaxfeed.App.db.entryDao().observeAllUnreadIds(listDisplayDate, isDesc)
            binding.bottomNavigation.selectedItemId == R.id.favorites -> com.duggalsu.flaxfeed.App.db.entryDao().observeAllFavoriteIds(listDisplayDate, isDesc)
            else -> com.duggalsu.flaxfeed.App.db.entryDao().observeAllIds(listDisplayDate, isDesc)
        }

        entryIdsLiveData?.observe(viewLifecycleOwner, Observer { list ->
            entryIds = list
        })

        entriesLiveData?.removeObservers(viewLifecycleOwner)
        entriesLiveData = LivePagedListBuilder(when {
            searchText != null -> com.duggalsu.flaxfeed.App.db.entryDao().observeSearch(searchText!!, isDesc)
            feed?.isGroup == true && binding.bottomNavigation.selectedItemId == R.id.unreads -> com.duggalsu.flaxfeed.App.db.entryDao().observeUnreadsByGroup(feed!!.id, listDisplayDate, isDesc)
            feed?.isGroup == true && binding.bottomNavigation.selectedItemId == R.id.favorites -> com.duggalsu.flaxfeed.App.db.entryDao().observeFavoritesByGroup(feed!!.id, listDisplayDate, isDesc)
            feed?.isGroup == true -> com.duggalsu.flaxfeed.App.db.entryDao().observeByGroup(feed!!.id, listDisplayDate, isDesc)

            feed != null && feed?.id != Feed.ALL_ENTRIES_ID && binding.bottomNavigation.selectedItemId == R.id.unreads -> com.duggalsu.flaxfeed.App.db.entryDao().observeUnreadsByFeed(feed!!.id, listDisplayDate, isDesc)
            feed != null && feed?.id != Feed.ALL_ENTRIES_ID && binding.bottomNavigation.selectedItemId == R.id.favorites -> com.duggalsu.flaxfeed.App.db.entryDao().observeFavoritesByFeed(feed!!.id, listDisplayDate, isDesc)
            feed != null && feed?.id != Feed.ALL_ENTRIES_ID -> com.duggalsu.flaxfeed.App.db.entryDao().observeByFeed(feed!!.id, listDisplayDate, isDesc)

            binding.bottomNavigation.selectedItemId == R.id.unreads -> com.duggalsu.flaxfeed.App.db.entryDao().observeAllUnreads(listDisplayDate, isDesc)
            binding.bottomNavigation.selectedItemId == R.id.favorites -> com.duggalsu.flaxfeed.App.db.entryDao().observeAllFavorites(listDisplayDate, isDesc)
            else -> com.duggalsu.flaxfeed.App.db.entryDao().observeAll(listDisplayDate, isDesc)
        }, 30).build()

        entriesLiveData?.observe(viewLifecycleOwner, Observer { pagedList ->
            adapter.submitList(pagedList)
        })

        newCountLiveData?.removeObservers(viewLifecycleOwner)
        newCountLiveData = when {
            feed?.isGroup == true -> com.duggalsu.flaxfeed.App.db.entryDao().observeNewEntriesCountByGroup(feed!!.id, listDisplayDate)
            feed != null && feed?.id != Feed.ALL_ENTRIES_ID -> com.duggalsu.flaxfeed.App.db.entryDao().observeNewEntriesCountByFeed(feed!!.id, listDisplayDate)
            else -> com.duggalsu.flaxfeed.App.db.entryDao().observeNewEntriesCount(listDisplayDate)
        }

        newCountLiveData?.observe(viewLifecycleOwner, Observer { count ->
            if (count != null && count > 0L) {
                // If we have an empty list, let's immediately display the new items
                if (entryIds?.isEmpty() == true && binding.bottomNavigation.selectedItemId != R.id.favorites) {
                    listDisplayDate = Date().time
                    initDataObservers()
                } else {
                    unreadBadge?.badgeNumber = count.toInt()
                }
            } else {
                unreadBadge?.hide(false)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        context?.registerOnPrefChangeListener(prefListener)
        refreshSwipeProgress()

        val displayThumbnails = context?.getPrefBoolean(PrefConstants.DISPLAY_THUMBNAILS, true) == true
        if (displayThumbnails != adapter.displayThumbnails) {
            adapter.displayThumbnails = displayThumbnails
            adapter.notifyDataSetChanged()
        }

        val hideFAB = context?.getPrefBoolean(PrefConstants.HIDE_BUTTON_MARK_ALL_AS_READ, false) == true

        if (context?.getPrefBoolean(PrefConstants.HIDE_NAVIGATION_ON_SCROLL, false) == true) {
            binding.bottomNavigation.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                if (behavior !is HideBottomViewOnScrollBehavior) {
                    behavior = HideBottomViewOnScrollBehavior<BottomNavigationView>()
                }
            }
            showNavigationIfRecyclerViewCannotScroll()
            fabScrollListener?.let {
                binding.recyclerView.removeOnScrollListener(it)
                fabScrollListener = null
            }
            if (hideFAB) {
                binding.readAllFab.hide()
            } else {
                if (isBottomNavigationViewShown()) {
                    binding.readAllFab.show()
                }
                fabScrollListener = object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy > 0 && binding.readAllFab.isShown) {
                            binding.readAllFab.hide()
                        } else if (dy < 0 && !binding.readAllFab.isShown) {
                            binding.readAllFab.show()
                        }
                    }
                }
                binding.recyclerView.addOnScrollListener(fabScrollListener!!)
            }
            binding.toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
            }
            activity?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
            ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { _, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.coordinator.updateLayoutParams<FrameLayout.LayoutParams> {
                    leftMargin = systemInsets.left
                    rightMargin = systemInsets.right
                }
                binding.recyclerView.updatePadding(bottom = resources.getDimensionPixelSize(R.dimen.recycler_view_vertical_padding) + systemInsets.bottom)
                binding.bottomNavigation.updatePadding(bottom = systemInsets.bottom)
                binding.bottomNavigation.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    height = resources.getDimensionPixelSize(R.dimen.bottom_nav_height) + systemInsets.bottom
                }
                binding.toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                    topMargin = systemInsets.top
                }
                insets
            }
            val statusBarBackground = ResourcesCompat.getColor(resources, R.color.status_bar_background, null)
            activity?.window?.statusBarColor = statusBarBackground
            activity?.window?.navigationBarColor = if (context?.isGestureNavigationEnabled() == true) Color.TRANSPARENT else statusBarBackground
        } else {
            binding.coordinator.updateLayoutParams<FrameLayout.LayoutParams> {
                leftMargin = 0
                rightMargin = 0
            }
            binding.recyclerView.updatePadding(bottom = resources.getDimensionPixelSize(R.dimen.recycler_view_bottom_padding_with_nav))
            binding.bottomNavigation.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                if (behavior is HideBottomViewOnScrollBehavior) {
                    (behavior as HideBottomViewOnScrollBehavior).slideUp(binding.bottomNavigation)
                }
                behavior = null
                height = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            }
            binding.bottomNavigation.updatePadding(bottom = 0)
            fabScrollListener?.let {
                binding.recyclerView.removeOnScrollListener(it)
                fabScrollListener = null
            }
            if (hideFAB) {
                binding.readAllFab.hide()
            } else {
                binding.readAllFab.show()
            }
            binding.appbar.setExpanded(true, true)
            binding.toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags = 0
                topMargin = 0
            }
            activity?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
            }
            ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, null)
            val tv = TypedValue()
            if (activity?.theme?.resolveAttribute(R.attr.colorPrimaryDark, tv, true) == true) {
                activity?.window?.statusBarColor = tv.data
                activity?.window?.navigationBarColor = tv.data
            }
        }
    }

    override fun onStop() {
        super.onStop()
        context?.unregisterOnPrefChangeListener(prefListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(STATE_FEED, feed)
        outState.putString(STATE_SELECTED_ENTRY_ID, adapter.selectedEntryId)
        outState.putLong(STATE_LIST_DISPLAY_DATE, listDisplayDate)
        outState.putString(STATE_SEARCH_TEXT, searchText)

        super.onSaveInstanceState(outState)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.setHasFixedSize(true)

        val layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setColorScheme(R.color.colorAccent,
                requireContext().attr(R.attr.colorPrimaryDark).resourceId,
                R.color.colorAccent,
                requireContext().attr(R.attr.colorPrimaryDark).resourceId)

        binding.refreshLayout.setOnRefreshListener {
            startRefresh()
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            private val VELOCITY = dip(800).toFloat()

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
                return VELOCITY
            }

            override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
                return VELOCITY
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                adapter.currentList?.get(viewHolder.adapterPosition)?.let { entryWithFeed ->
                    entryWithFeed.entry.read = !entryWithFeed.entry.read
                    doAsync {
                        val snackbarMessage: Int
                        if (entryWithFeed.entry.read) {
                            com.duggalsu.flaxfeed.App.db.entryDao().markAsRead(listOf(entryWithFeed.entry.id))
                            snackbarMessage = R.string.marked_as_read
                        } else {
                            com.duggalsu.flaxfeed.App.db.entryDao().markAsUnread(listOf(entryWithFeed.entry.id))
                            snackbarMessage = R.string.marked_as_unread
                        }

                        Snackbar
                                .make(binding.coordinator, snackbarMessage, Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) { _ ->
                                    doAsync {
                                        if (entryWithFeed.entry.read) {
                                            com.duggalsu.flaxfeed.App.db.entryDao().markAsUnread(listOf(entryWithFeed.entry.id))
                                        } else {
                                            com.duggalsu.flaxfeed.App.db.entryDao().markAsRead(listOf(entryWithFeed.entry.id))
                                        }
                                    }
                                }
                                .apply {
                                    view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                        anchorId = R.id.bottom_navigation
                                        anchorGravity = Gravity.TOP
                                        gravity = Gravity.TOP
                                        insetEdge = Gravity.BOTTOM
                                    }
                                    show()
                                }

                        uiThread {
                            showNavigationIfRecyclerViewCannotScroll()
                        }

                        if (binding.bottomNavigation.selectedItemId != R.id.unreads) {
                            uiThread {
                                adapter.notifyItemChanged(viewHolder.adapterPosition)
                            }
                        }
                    }
                }
            }
        }

        // attaching the touch helper to recycler view
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerView)

        binding.recyclerView.emptyView = binding.emptyView

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                activity?.closeKeyboard()
            }
        })
    }

    private fun startRefresh() {
        if (context?.getPrefBoolean(PrefConstants.IS_REFRESHING, false) == false) {
            if (feed?.id != Feed.ALL_ENTRIES_ID) {
                context?.startService(Intent(context, FetcherService::class.java).setAction(FetcherService.ACTION_REFRESH_FEEDS).putExtra(FetcherService.EXTRA_FEED_ID,
                        feed?.id))
            } else {
                context?.startService(Intent(context, FetcherService::class.java).setAction(FetcherService.ACTION_REFRESH_FEEDS))
            }
        }

        // In case there is no internet, the service won't even start, let's quickly stop the refresh animation
        binding.refreshLayout.postDelayed({ refreshSwipeProgress() }, 500)
    }

    private fun setupTitle() {
        binding.toolbar.apply {
            if (feed == null || feed?.id == Feed.ALL_ENTRIES_ID) {
                titleResource = R.string.all_entries
            } else {
                title = feed?.title
            }
        }
    }

    private fun showNavigationIfRecyclerViewCannotScroll() {
        val hideFAB = context?.getPrefBoolean(PrefConstants.HIDE_BUTTON_MARK_ALL_AS_READ, false) == true
        val canScrollRecyclerView = binding.recyclerView.canScrollVertically(1) ||
                binding.recyclerView.canScrollVertically(-1)
        binding.bottomNavigation.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            if (!canScrollRecyclerView) {
                (behavior as? HideBottomViewOnScrollBehavior)?.slideUp(binding.bottomNavigation)
            }
        }
        if (!canScrollRecyclerView) {
            binding.appbar.setExpanded(true, true)
            if (!hideFAB) {
                binding.readAllFab.show()
            }
        }
    }

    private fun isBottomNavigationViewShown(): Boolean {
        val location = IntArray(2)
        binding.bottomNavigation.getLocationOnScreen(location)
        return location[1] < resources.displayMetrics.heightPixels
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_fragment_entries, menu)

        menu.findItem(R.id.menu_entries__share).isVisible = binding.bottomNavigation.selectedItemId == R.id.favorites

        val searchItem = menu.findItem(R.id.menu_entries__search)
        val searchView = searchItem.actionView as SearchView
        if (searchText != null) {
            searchItem.expandActionView()
            searchView.post {
                searchView.setQuery(searchText, false)
                searchView.clearFocus()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (searchText != null) { // needed because it can actually be called after the onMenuItemActionCollapse event
                    searchText = newText

                    // In order to avoid plenty of request, we add a small throttle time
                    searchHandler.removeCallbacksAndMessages(null)
                    searchHandler.postDelayed({
                        initDataObservers()
                    }, 700)
                }
                return false
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchText = ""
                initDataObservers()
                binding.bottomNavigation.isGone = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchText = null
                initDataObservers()
                binding.bottomNavigation.isVisible = true
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_entries__share -> {
                // TODO: will only work for the visible 30 items, need to find something better
                adapter.currentList?.joinToString("\n\n") { it.entry.title + ": " + it.entry.link }?.let { content ->
                    val title = getString(R.string.app_name) + " " + getString(R.string.favorites)
                    share(content.take(300000), title) // take() to avoid crashing with a too big intent
                }
            }
            R.id.menu_entries__about -> {
                navigator.goToAboutMe()
            }
            R.id.menu_entries__settings -> {
                navigator.goToSettings()
            }
        }

        return true
    }

    fun setSelectedEntryId(selectedEntryId: String) {
        adapter.selectedEntryId = selectedEntryId
    }

    private fun refreshSwipeProgress() {
        binding.refreshLayout.isRefreshing = context?.getPrefBoolean(PrefConstants.IS_REFRESHING, false)
                ?: false
    }
}
