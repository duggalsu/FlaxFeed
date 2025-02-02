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

package com.duggalsu.flaxfeed.ui.feeds

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bignerdranch.expandablerecyclerview.ChildViewHolder
import com.bignerdranch.expandablerecyclerview.ExpandableRecyclerAdapter
import com.bignerdranch.expandablerecyclerview.ParentViewHolder
import com.duggalsu.flaxfeed.R
import com.duggalsu.flaxfeed.databinding.ViewFeedBinding
import com.duggalsu.flaxfeed.data.entities.Feed
import com.duggalsu.flaxfeed.data.entities.FeedWithCount
import com.duggalsu.flaxfeed.data.utils.PrefConstants
import com.duggalsu.flaxfeed.utils.getPrefString
import org.jetbrains.anko.dip
import org.jetbrains.anko.sdk21.listeners.onClick
import org.jetbrains.anko.sdk21.listeners.onLongClick


abstract class BaseFeedAdapter(groups: List<FeedGroup>) : ExpandableRecyclerAdapter<FeedGroup, FeedWithCount, BaseFeedAdapter.FeedGroupViewHolder, BaseFeedAdapter.FeedViewHolder>(groups) {

    companion object {
        const val TYPE_TOP_LEVEL = 0
        const val TYPE_CHILD = 1
    }

    var feedClickListener: ((View, FeedWithCount) -> Unit)? = null
    var feedLongClickListener: ((View, FeedWithCount) -> Unit)? = null

    open val layoutId = R.layout.view_feed

    open fun bindItem(itemView: View, feedWithCount: FeedWithCount) {
    }

    open fun bindItem(itemView: View, group: FeedGroup) {
    }

    fun onFeedClick(listener: (View, FeedWithCount) -> Unit) {
        feedClickListener = listener
    }

    fun onFeedLongClick(listener: (View, FeedWithCount) -> Unit) {
        feedLongClickListener = listener
    }

    override fun getItemId(position: Int) =
            getFeedAtPos(position).feed.id

    fun getFeedAtPos(position: Int): FeedWithCount {
        val item = mFlatItemList[position]
        return if (item.isParent) {
            mFlatItemList[position].parent.feedWithCount
        } else {
            mFlatItemList[position].child
        }
    }

    override fun getParentViewType(parentPosition: Int): Int {
        return TYPE_TOP_LEVEL
    }

    override fun getChildViewType(parentPosition: Int, childPosition: Int): Int {
        return TYPE_CHILD
    }

    private lateinit var binding: ViewFeedBinding
    override fun onCreateParentViewHolder(parentViewGroup: ViewGroup, viewType: Int): FeedGroupViewHolder {
        binding = ViewFeedBinding.inflate(LayoutInflater.from(parentViewGroup.context), parentViewGroup, false)
        return FeedGroupViewHolder(binding.root)
    }

    override fun onCreateChildViewHolder(childViewGroup: ViewGroup, viewType: Int): FeedViewHolder {
        binding = ViewFeedBinding.inflate(LayoutInflater.from(childViewGroup.context), childViewGroup, false)
        return FeedViewHolder(binding.root)
    }

    override fun onBindParentViewHolder(groupViewHolder: FeedGroupViewHolder, parentPosition: Int, group: FeedGroup) {
        groupViewHolder.bindItem(group)
    }

    override fun onBindChildViewHolder(feedViewHolder: FeedViewHolder, parentPosition: Int, childPosition: Int, feed: FeedWithCount) {
        feedViewHolder.bindItem(feed)
    }

    inner class FeedGroupViewHolder(itemView: View)
        : ParentViewHolder<FeedGroup, FeedWithCount>(itemView) {

        fun bindItem(group: FeedGroup) {
            if (group.feedWithCount.feed.isGroup) {
                if (isExpanded) {
                    binding.icon.setImageResource(
                            when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                                "LIGHT" -> R.drawable.ic_keyboard_arrow_up_black_24dp
                                else -> R.drawable.ic_keyboard_arrow_up_white_24dp
                            })
                    binding.icon.contentDescription = R.string.collapse_arrow_content_description.toString()
                } else {
                    binding.icon.setImageResource(
                            when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                                "LIGHT" -> R.drawable.ic_keyboard_arrow_down_black_24dp
                                else -> R.drawable.ic_keyboard_arrow_down_white_24dp
                            })
                    binding.icon.contentDescription = R.string.expand_arrow_content_description.toString()
                }

                binding.icon.isClickable = true
                binding.icon.onClick {
                    if (isExpanded) {
                        binding.icon.setImageResource(
                                when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                                    "LIGHT" -> R.drawable.ic_keyboard_arrow_down_black_24dp
                                    else -> R.drawable.ic_keyboard_arrow_down_white_24dp
                                })
                        collapseView()
                    } else {
                        binding.icon.setImageResource(
                                when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                                    "LIGHT" -> R.drawable.ic_keyboard_arrow_up_black_24dp
                                    else -> R.drawable.ic_keyboard_arrow_up_white_24dp
                                })
                        expandView()
                    }
                }
            } else {
                binding.icon.isClickable = false
                if (group.feedWithCount.feed.id == Feed.ALL_ENTRIES_ID) {
                    binding.icon.setImageResource(
                            when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                                "LIGHT" -> R.drawable.ic_list_black_24dp
                                else -> R.drawable.ic_list_white_24dp
                            })
                } else {
                    binding.icon.setImageDrawable(group.feedWithCount.feed.getLetterDrawable(true))
                }
            }
            binding.title.text = group.feedWithCount.feed.title
            binding.entryCount?.text = group.getEntryCountString()
            if (group.feedWithCount.feed.fetchError || group.subFeeds.any { it.feed.fetchError }) {
                binding.title.setTextColor(Color.RED) //TODO better
            } else {
                binding.title.setTextColor(
                        when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                            "LIGHT" -> Color.BLACK
                            else -> Color.WHITE
                        })
            }
            itemView.setPadding(0, 0, 0, 0)
            itemView.onClick {
                feedClickListener?.invoke(itemView, group.feedWithCount)
            }
            itemView.onLongClick {
                feedLongClickListener?.invoke(itemView, group.feedWithCount)
                true
            }

            bindItem(itemView, group)
        }

        override fun shouldItemViewClickToggleExpansion(): Boolean = false
    }

    inner class FeedViewHolder(itemView: View) : ChildViewHolder<FeedWithCount>(itemView) {

        fun bindItem(feedWithCount: FeedWithCount) {
            binding.title.text = feedWithCount.feed.title
            binding.entryCount?.text = feedWithCount.getEntryCountString()
            if (feedWithCount.feed.fetchError) { //TODO better
                binding.title.setTextColor(Color.RED)
            } else {
                binding.title.setTextColor(
                        when (itemView.context.getPrefString(PrefConstants.THEME, "DARK")) {
                            "LIGHT" -> Color.BLACK
                            else -> Color.WHITE
                        })
            }
            binding.icon.isClickable = false
            binding.icon.setImageDrawable(feedWithCount.feed.getLetterDrawable(true))
            itemView.setPadding(itemView.dip(30), 0, 0, 0)
            itemView.onClick {
                feedClickListener?.invoke(itemView, feedWithCount)
            }
            itemView.onLongClick {
                feedLongClickListener?.invoke(itemView, feedWithCount)
                true
            }

            bindItem(itemView, feedWithCount)
        }
    }
}