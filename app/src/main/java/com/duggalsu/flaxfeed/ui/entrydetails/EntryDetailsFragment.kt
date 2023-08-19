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

package com.duggalsu.flaxfeed.ui.entrydetails

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.google.android.material.appbar.AppBarLayout
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeGestureListener
import com.duggalsu.flaxfeed.R
import com.duggalsu.flaxfeed.databinding.FragmentEntryDetailsBinding
import com.duggalsu.flaxfeed.databinding.FragmentEntryDetailsNoswipeBinding
import com.duggalsu.flaxfeed.data.entities.EntryWithFeed
import com.duggalsu.flaxfeed.data.utils.PrefConstants
import com.duggalsu.flaxfeed.data.utils.PrefConstants.ENABLE_SWIPE_ENTRY
import com.duggalsu.flaxfeed.data.utils.PrefConstants.HIDE_NAVIGATION_ON_SCROLL
import com.duggalsu.flaxfeed.service.FetcherService
import com.duggalsu.flaxfeed.ui.main.MainActivity
import com.duggalsu.flaxfeed.ui.main.MainNavigator
import com.duggalsu.flaxfeed.utils.getPrefBoolean
import com.duggalsu.flaxfeed.utils.isGestureNavigationEnabled
import com.duggalsu.flaxfeed.utils.isOnline
import org.jetbrains.anko.attr
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.support.v4.browse
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.share
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.uiThread
import org.jetbrains.annotations.NotNull


class EntryDetailsFragment : Fragment() {

    companion object {

        const val ARG_ENTRY_ID = "ARG_ENTRY_ID"
        const val ARG_ALL_ENTRIES_IDS = "ARG_ALL_ENTRIES_IDS"

        fun newInstance(entryId: String, allEntryIds: List<String>): EntryDetailsFragment {
            return EntryDetailsFragment().apply {
                arguments = bundleOf(ARG_ENTRY_ID to entryId, ARG_ALL_ENTRIES_IDS to allEntryIds)
            }
        }
    }

    private val navigator: MainNavigator? by lazy { activity as? MainNavigator }

    private lateinit var entryId: String
    private var entryWithFeed: EntryWithFeed? = null
    private var allEntryIds = emptyList<String>()
        set(value) {
            field = value

            val currentIdx = value.indexOf(entryId)

            previousId = if (currentIdx <= 0) {
                null
            } else {
                value[currentIdx - 1]
            }

            nextId = if (currentIdx < 0 || currentIdx >= value.size - 1) {
                null
            } else {
                value[currentIdx + 1]
            }
        }
    private var previousId: String? = null
    private var nextId: String? = null
    private var isMobilizingLiveData: LiveData<Int>? = null
    private var isMobilizing = false
    private var preferFullText = true

    private var _binding: FragmentEntryDetailsBinding? = null
    private var _binding_noswipe: FragmentEntryDetailsNoswipeBinding? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding = FragmentEntryDetailsBinding.inflate(inflater, container, false)
            val view = _binding!!.root
            view
        } else {
            _binding_noswipe = FragmentEntryDetailsNoswipeBinding.inflate(inflater, container, false)
            val view = _binding_noswipe!!.root
            view
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _binding_noswipe = null
        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding?.entryView?.destroy()
        } else {
            _binding_noswipe?.entryView?.destroy()

        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding?.refreshLayout?.setColorScheme(R.color.colorAccent,
                requireContext().attr(R.attr.colorPrimaryDark).resourceId,
                R.color.colorAccent,
                requireContext().attr(R.attr.colorPrimaryDark).resourceId)
        } else {
            _binding_noswipe?.refreshLayout?.setColorScheme(R.color.colorAccent,
                requireContext().attr(R.attr.colorPrimaryDark).resourceId,
                R.color.colorAccent,
                requireContext().attr(R.attr.colorPrimaryDark).resourceId)
        }

        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding?.refreshLayout?.setOnRefreshListener {
                switchFullTextMode()
            }
        } else {
            _binding_noswipe?.refreshLayout?.setOnRefreshListener {
                switchFullTextMode()
            }
        }

        if (defaultSharedPreferences.getString(PrefConstants.THEME, null) == "LIGHT") {
            _binding?.navigateBefore?.imageResource = R.drawable.ic_navigate_before_black_24dp
            _binding?.navigateNext?.imageResource = R.drawable.ic_navigate_next_black_24dp
        }

        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding?.swipeView?.swipeGestureListener = object : SwipeGestureListener {
                override fun onSwipedLeft(@NotNull swipeActionView: SwipeActionView): Boolean {
                    nextId?.let { nextId ->
                        setEntry(nextId, allEntryIds)
                        navigator?.setSelectedEntryId(nextId)
                        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                            _binding?.appBarLayout?.setExpanded(true, true)
                            _binding?.nestedScrollView?.scrollTo(0, 0)
                        } else {
                            _binding_noswipe?.appBarLayout?.setExpanded(true, true)
                            _binding_noswipe?.nestedScrollView?.scrollTo(0, 0)
                        }
                    }
                    return true
                }

                override fun onSwipedRight(@NotNull swipeActionView: SwipeActionView): Boolean {
                    previousId?.let { previousId ->
                        setEntry(previousId, allEntryIds)
                        navigator?.setSelectedEntryId(previousId)
                        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                            _binding?.appBarLayout?.setExpanded(true, true)
                            _binding?.nestedScrollView?.scrollTo(0, 0)
                        } else {
                            _binding_noswipe?.appBarLayout?.setExpanded(true, true)
                            _binding_noswipe?.nestedScrollView?.scrollTo(0, 0)
                        }
                    }
                    return true
                }
            }
        }

        if (defaultSharedPreferences.getBoolean(HIDE_NAVIGATION_ON_SCROLL, false)) {
            if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                _binding?.toolbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
                    scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                }
            } else {
                _binding_noswipe?.toolbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
                    scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                }
            }
            activity?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }

            if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                ViewCompat.setOnApplyWindowInsetsListener(_binding?.toolbar!!) { _, insets ->
                    val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    if (_binding?.swipeView != null) {
                        _binding?.swipeView!!.updateLayoutParams<FrameLayout.LayoutParams> {
                            leftMargin = systemInsets.left
                            rightMargin = systemInsets.right
                        }
                    } else {
                        _binding?.coordinator?.updateLayoutParams<FrameLayout.LayoutParams> {
                            leftMargin = systemInsets.left
                            rightMargin = systemInsets.right
                        }
                    }
                    _binding?.toolbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
                        topMargin = systemInsets.top
                    }
                    _binding?.entryView?.updateLayoutParams<FrameLayout.LayoutParams> {
                        bottomMargin = systemInsets.bottom
                    }
                    insets
                }
            } else {
                ViewCompat.setOnApplyWindowInsetsListener(_binding_noswipe?.toolbar!!) { _, insets ->
                    val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    if (_binding?.swipeView != null) {
                        _binding?.swipeView!!.updateLayoutParams<FrameLayout.LayoutParams> {
                            leftMargin = systemInsets.left
                            rightMargin = systemInsets.right
                        }
                    } else {
                        _binding_noswipe?.coordinator?.updateLayoutParams<FrameLayout.LayoutParams> {
                            leftMargin = systemInsets.left
                            rightMargin = systemInsets.right
                        }
                    }
                    _binding_noswipe?.toolbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
                        topMargin = systemInsets.top
                    }
                    _binding_noswipe?.entryView?.updateLayoutParams<FrameLayout.LayoutParams> {
                        bottomMargin = systemInsets.bottom
                    }
                    insets
                }

            }

            val statusBarBackground = ResourcesCompat.getColor(resources, R.color.status_bar_background, null)
            activity?.window?.statusBarColor = statusBarBackground
            activity?.window?.navigationBarColor = if (context?.isGestureNavigationEnabled() == true) Color.TRANSPARENT else statusBarBackground
        }

        setEntry(arguments?.getString(ARG_ENTRY_ID)!!, arguments?.getStringArrayList(ARG_ALL_ENTRIES_IDS)!!)
    }

    private fun initDataObservers() {
        isMobilizingLiveData?.removeObservers(viewLifecycleOwner)

        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding?.refreshLayout?.isRefreshing = false
        } else {
            _binding_noswipe?.refreshLayout?.isRefreshing = false
        }

        isMobilizingLiveData = com.duggalsu.flaxfeed.App.db.taskDao().observeItemMobilizationTasksCount(entryId)
        isMobilizingLiveData?.observe(viewLifecycleOwner, { count ->
            if (count ?: 0 > 0) {
                isMobilizing = true
                if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                    _binding?.refreshLayout?.isRefreshing = true
                } else {
                    _binding_noswipe?.refreshLayout?.isRefreshing = true
                }

                // If the service is not started, start it here to avoid an infinite loading
                if (context?.getPrefBoolean(PrefConstants.IS_REFRESHING, false) == false) {
                    context?.startService(Intent(context, FetcherService::class.java).setAction(FetcherService.ACTION_MOBILIZE_FEEDS))
                }
            } else {
                if (isMobilizing) {
                    doAsync {
                        com.duggalsu.flaxfeed.App.db.entryDao().findByIdWithFeed(entryId)?.let { newEntry ->
                            uiThread {
                                entryWithFeed = newEntry
                                if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                                    _binding?.entryView?.setEntry(entryWithFeed, true)
                                } else {
                                    _binding_noswipe?.entryView?.setEntry(entryWithFeed, true)
                                }

                                setupToolbar()
                            }
                        }
                    }
                }

                isMobilizing = false
                if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                    _binding?.refreshLayout?.isRefreshing = false
                } else {
                    _binding_noswipe?.refreshLayout?.isRefreshing = false
                }
            }
        })
    }

    private fun setupToolbar() {
        val mainActivity = requireActivity() as MainActivity
        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            _binding?.toolbar?.apply {
                entryWithFeed?.let { entryWithFeed ->
                    title = entryWithFeed.feedTitle

                    menu.clear()
                    inflateMenu(R.menu.menu_fragment_entry_details)

                    if (mainActivity.binding?.containersLayout?.hasTwoColumns() != true) {
                        setNavigationIcon(R.drawable.ic_back_white_24dp)
                        setNavigationOnClickListener { activity?.onBackPressed() }
                    }

                    if (entryWithFeed.entry.favorite) {
                        menu.findItem(R.id.menu_entry_details__favorite)
                            .setTitle(R.string.menu_unstar)
                            .setIcon(R.drawable.ic_star_white_24dp)
                    }


                    if (entryWithFeed.entry.mobilizedContent == null || !preferFullText) {
                        menu.findItem(R.id.menu_entry_details__fulltext).isVisible = true
                        menu.findItem(R.id.menu_entry_details__original_text).isVisible = false
                    } else {
                        menu.findItem(R.id.menu_entry_details__fulltext).isVisible = false
                        menu.findItem(R.id.menu_entry_details__original_text).isVisible = true
                    }

                    setOnMenuItemClickListener { item ->
                        when (item?.itemId) {
                            R.id.menu_entry_details__favorite -> {
                                entryWithFeed.entry.favorite = !entryWithFeed.entry.favorite
                                entryWithFeed.entry.read = true // otherwise it marked it as unread again

                                if (entryWithFeed.entry.favorite) {
                                    item.setTitle(R.string.menu_unstar).setIcon(R.drawable.ic_star_white_24dp)
                                } else {
                                    item.setTitle(R.string.menu_star).setIcon(R.drawable.ic_star_border_white_24dp)
                                }

                                doAsync {
                                    com.duggalsu.flaxfeed.App.db.entryDao().update(entryWithFeed.entry)
                                }
                            }
                            R.id.menu_entry_details__open_browser -> {
                                entryWithFeed.entry.link?.let {
                                    browse(it)
                                }
                            }
                            R.id.menu_entry_details__share -> {
                                share(entryWithFeed.entry.link.orEmpty(), entryWithFeed.entry.title.orEmpty())
                            }
                            R.id.menu_entry_details__fulltext -> {
                                switchFullTextMode()
                            }
                            R.id.menu_entry_details__original_text -> {
                                switchFullTextMode()
                            }
                            R.id.menu_entry_details__mark_as_unread -> {
                                doAsync {
                                    com.duggalsu.flaxfeed.App.db.entryDao().markAsUnread(listOf(entryId))
                                }
                                if (mainActivity.binding?.containersLayout?.hasTwoColumns() != true) {
                                    activity?.onBackPressed()
                                }
                            }
                        }

                        true
                    }
                }
            }
        } else {
            _binding_noswipe?.toolbar?.apply {
                entryWithFeed?.let { entryWithFeed ->
                    title = entryWithFeed.feedTitle

                    menu.clear()
                    inflateMenu(R.menu.menu_fragment_entry_details)

                    if (mainActivity.binding?.containersLayout?.hasTwoColumns() != true) {
                        setNavigationIcon(R.drawable.ic_back_white_24dp)
                        setNavigationOnClickListener { activity?.onBackPressed() }
                    }

                    if (entryWithFeed.entry.favorite) {
                        menu.findItem(R.id.menu_entry_details__favorite)
                            .setTitle(R.string.menu_unstar)
                            .setIcon(R.drawable.ic_star_white_24dp)
                    }


                    if (entryWithFeed.entry.mobilizedContent == null || !preferFullText) {
                        menu.findItem(R.id.menu_entry_details__fulltext).isVisible = true
                        menu.findItem(R.id.menu_entry_details__original_text).isVisible = false
                    } else {
                        menu.findItem(R.id.menu_entry_details__fulltext).isVisible = false
                        menu.findItem(R.id.menu_entry_details__original_text).isVisible = true
                    }

                    setOnMenuItemClickListener { item ->
                        when (item?.itemId) {
                            R.id.menu_entry_details__favorite -> {
                                entryWithFeed.entry.favorite = !entryWithFeed.entry.favorite
                                entryWithFeed.entry.read = true // otherwise it marked it as unread again

                                if (entryWithFeed.entry.favorite) {
                                    item.setTitle(R.string.menu_unstar).setIcon(R.drawable.ic_star_white_24dp)
                                } else {
                                    item.setTitle(R.string.menu_star).setIcon(R.drawable.ic_star_border_white_24dp)
                                }

                                doAsync {
                                    com.duggalsu.flaxfeed.App.db.entryDao().update(entryWithFeed.entry)
                                }
                            }
                            R.id.menu_entry_details__open_browser -> {
                                entryWithFeed.entry.link?.let {
                                    browse(it)
                                }
                            }
                            R.id.menu_entry_details__share -> {
                                share(entryWithFeed.entry.link.orEmpty(), entryWithFeed.entry.title.orEmpty())
                            }
                            R.id.menu_entry_details__fulltext -> {
                                switchFullTextMode()
                            }
                            R.id.menu_entry_details__original_text -> {
                                switchFullTextMode()
                            }
                            R.id.menu_entry_details__mark_as_unread -> {
                                doAsync {
                                    com.duggalsu.flaxfeed.App.db.entryDao().markAsUnread(listOf(entryId))
                                }
                                if (mainActivity.binding?.containersLayout?.hasTwoColumns() != true) {
                                    activity?.onBackPressed()
                                }
                            }
                        }

                        true
                    }
                }
            }
        }
    }

    private fun switchFullTextMode() {
        // Enable this to test new manual mobilization
//		doAsync {
//			entryWithFeed?.entry?.let {
//				it.mobilizedContent = null
//				App.db.entryDao().insert(it)
//			}
//		}

        entryWithFeed?.let { entryWithFeed ->
            if (entryWithFeed.entry.mobilizedContent == null || !preferFullText) {
                if (entryWithFeed.entry.mobilizedContent == null) {
                    this@EntryDetailsFragment.context?.let { c ->
                        if (c.isOnline()) {
                            doAsync {
                                FetcherService.addEntriesToMobilize(listOf(entryWithFeed.entry.id))
                                c.startService(Intent(c, FetcherService::class.java).setAction(FetcherService.ACTION_MOBILIZE_FEEDS))
                            }
                        } else {
                            if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                                _binding?.refreshLayout?.isRefreshing = false
                            } else {
                                _binding_noswipe?.refreshLayout?.isRefreshing = false
                            }
                            toast(R.string.network_error).show()
                        }
                    }
                } else {
                    if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                        _binding?.refreshLayout?.isRefreshing = false
                    } else {
                        _binding_noswipe?.refreshLayout?.isRefreshing = false
                    }
                    preferFullText = true
                    if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                        _binding?.entryView?.setEntry(entryWithFeed, preferFullText)
                    } else {
                        _binding_noswipe?.entryView?.setEntry(entryWithFeed, preferFullText)
                    }

                    setupToolbar()
                }
            } else {
                if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                    _binding?.refreshLayout?.isRefreshing = isMobilizing
                } else {
                    _binding_noswipe?.refreshLayout?.isRefreshing = isMobilizing
                }
                preferFullText = false
                if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                    _binding?.entryView?.setEntry(entryWithFeed, preferFullText)
                } else {
                    _binding_noswipe?.entryView?.setEntry(entryWithFeed, preferFullText)
                }

                setupToolbar()
            }
        }
    }

    fun setEntry(entryId: String, allEntryIds: List<String>) {
        this.entryId = entryId
        this.allEntryIds = allEntryIds
        arguments?.putString(ARG_ENTRY_ID, entryId)
        arguments?.putStringArrayList(ARG_ALL_ENTRIES_IDS, ArrayList(allEntryIds))

        doAsync {
            com.duggalsu.flaxfeed.App.db.entryDao().findByIdWithFeed(entryId)?.let { entry ->
                val feed = com.duggalsu.flaxfeed.App.db.feedDao().findById(entry.entry.feedId)
                entryWithFeed = entry
                preferFullText = feed?.retrieveFullText ?: true
                isMobilizing = false

                uiThread {
                    if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
                        _binding?.entryView?.setEntry(entryWithFeed, preferFullText)
                    } else {
                        _binding_noswipe?.entryView?.setEntry(entryWithFeed, preferFullText)
                    }

                    initDataObservers()

                    setupToolbar()
                }
            }

            com.duggalsu.flaxfeed.App.db.entryDao().markAsRead(listOf(entryId))
        }
    }
}
