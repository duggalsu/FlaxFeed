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

package net.frju.flym.ui.entries

import android.annotation.SuppressLint
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import net.fred.feedex.R
import net.fred.feedex.databinding.ViewEntryBinding
import net.fred.feedex.databinding.ViewMainContainersBinding
import net.frju.flym.data.entities.EntryWithFeed
import net.frju.flym.data.entities.Feed
import net.frju.flym.service.FetcherService
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.sdk21.listeners.onClick
import org.jetbrains.anko.sdk21.listeners.onLongClick
import org.jetbrains.anko.uiThread


class EntryAdapter(var displayThumbnails: Boolean, private val globalClickListener: (EntryWithFeed) -> Unit, private val globalLongClickListener: (EntryWithFeed) -> Unit, private val favoriteClickListener: (EntryWithFeed, ImageView) -> Unit) : PagedListAdapter<EntryWithFeed, EntryAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {

        @JvmField
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EntryWithFeed>() {

            override fun areItemsTheSame(oldItem: EntryWithFeed, newItem: EntryWithFeed): Boolean =
                    oldItem.entry.id == newItem.entry.id

            override fun areContentsTheSame(oldItem: EntryWithFeed, newItem: EntryWithFeed): Boolean =
                    oldItem.entry.id == newItem.entry.id && oldItem.entry.read == newItem.entry.read && oldItem.entry.favorite == newItem.entry.favorite // no need to do more complex in our case
        }

        @JvmField
        val CROSS_FADE_FACTORY: DrawableCrossFadeFactory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        @SuppressLint("SetTextI18n")
        fun bind(entryWithFeed: EntryWithFeed, globalClickListener: (EntryWithFeed) -> Unit, globalLongClickListener: (EntryWithFeed) -> Unit, favoriteClickListener: (EntryWithFeed, ImageView) -> Unit) = with(itemView) {
            doAsync {
                val mainImgUrl = if (TextUtils.isEmpty(entryWithFeed.entry.imageLink)) null else FetcherService.getDownloadedOrDistantImageUrl(entryWithFeed.entry.id, entryWithFeed.entry.imageLink!!)
                uiThread {
                    val letterDrawable = Feed.getLetterDrawable(entryWithFeed.entry.feedId, entryWithFeed.feedTitle)
                    if (mainImgUrl != null) {
                        com.bumptech.glide.Glide.with(context).load(mainImgUrl).centerCrop().transition(withCrossFade(CROSS_FADE_FACTORY)).placeholder(letterDrawable).error(letterDrawable).into(binding.mainIcon)
                    } else {
                        com.bumptech.glide.Glide.with(context).clear(binding.mainIcon)
                        binding.mainIcon.setImageDrawable(letterDrawable)
                    }

                    binding.mainIcon.visibility = if (displayThumbnails) View.VISIBLE else View.GONE

                    binding.title.isEnabled = !entryWithFeed.entry.read
                    binding.title.text = entryWithFeed.entry.title

                    binding.feedNameLayout.isEnabled = !entryWithFeed.entry.read
                    binding.feedNameLayout.text = entryWithFeed.feedTitle.orEmpty()

                    binding.date.isEnabled = !entryWithFeed.entry.read
                    binding.date.text = entryWithFeed.entry.getReadablePublicationDate(context)

                    binding.favoriteIcon.alpha = if (!entryWithFeed.entry.read) 1f else 0.5f

                    if (entryWithFeed.entry.favorite) {
                        binding.favoriteIcon.setImageResource(R.drawable.ic_star_24dp)
                    } else {
                        binding.favoriteIcon.setImageResource(R.drawable.ic_star_border_24dp)
                    }
                    binding.favoriteIcon.onClick { favoriteClickListener(entryWithFeed, binding.favoriteIcon) }

                    onClick { globalClickListener(entryWithFeed) }
                    onLongClick {
                        globalLongClickListener(entryWithFeed)
                        true
                    }
                }
            }
        }

        fun clear() = with(itemView) {
            com.bumptech.glide.Glide.with(context).clear(binding.mainIcon)
        }
    }

    private lateinit var binding: ViewEntryBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        binding = ViewEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val view = binding.root
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entryWithFeed = getItem(position)
        if (entryWithFeed != null) {
            holder.bind(entryWithFeed, globalClickListener, globalLongClickListener, favoriteClickListener)
        } else {
            // Null defines a placeholder item - PagedListAdapter will automatically invalidate
            // this row when the actual object is loaded from the database
            holder.clear()
        }

        holder.itemView.isSelected = (selectedEntryId == entryWithFeed?.entry?.id)
    }

    var selectedEntryId: String? = null
        set(newValue) {
            field = newValue
            notifyDataSetChanged()
        }
}