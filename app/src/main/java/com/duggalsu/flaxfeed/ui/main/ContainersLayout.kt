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

package com.duggalsu.flaxfeed.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ObjectAnimator.ofFloat
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.duggalsu.flaxfeed.R
import com.duggalsu.flaxfeed.databinding.ViewMainContainersBinding
import com.duggalsu.flaxfeed.utils.onLaidOut

class ContainersLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {

        const val ANIM_DURATION = 250

        private const val STATE_SUPER = "state_super"
        private const val STATE_CONTAINERS_STATE = "state_containers_state"
    }

    var state: MainNavigator.State? = null
        set(state) {
            field = state
            when (state) {
                MainNavigator.State.SINGLE_COLUMN_MASTER -> singleColumnMaster()
                MainNavigator.State.SINGLE_COLUMN_DETAILS -> singleColumnDetails()
                MainNavigator.State.TWO_COLUMNS_EMPTY -> twoColumnsEmpty()
                MainNavigator.State.TWO_COLUMNS_WITH_DETAILS -> twoColumnsWithDetails()
                else -> {}
            }
        }

    private var binding: ViewMainContainersBinding
    init {
        binding = ViewMainContainersBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun hasTwoColumns(): Boolean {
        return binding.twoColumnsContainer != null
    }

    private fun singleColumnMaster() {
        if (hasTwoColumns()) {
            binding.frameDetails.visibility = View.GONE
            binding.frameMaster.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            binding.frameMaster.layoutParams = binding.frameMaster.layoutParams
        } else {
            animateOutFrameDetails()
        }
        binding.frameMaster.visibility = View.VISIBLE
    }

    private fun singleColumnDetails() {
        binding.frameMaster.visibility = View.GONE
        binding.frameDetails.visibility = View.VISIBLE
    }

    private fun twoColumnsEmpty() {
        if (hasTwoColumns()) {
            setupSecondColumn()
        } else {
            animateOutFrameDetails()
        }
    }

    private fun twoColumnsWithDetails() {
        if (hasTwoColumns()) {
            setupSecondColumn()
        } else {
            animateInFrameDetails()
        }
    }

    private fun setupSecondColumn() {
        binding.frameMaster.run {
            isVisible = true
            layoutParams.width = context.resources.getDimensionPixelSize(R.dimen.container_max_width)
            layoutParams = binding.frameMaster.layoutParams
        }
        binding.frameDetails.run {
            isVisible = true
            (layoutParams as LayoutParams).marginStart = context.resources.getDimensionPixelSize(R.dimen.container_max_width)
            layoutParams = binding.frameDetails.layoutParams
        }
    }

    private fun animateInFrameDetails() {
        binding.frameMaster.isVisible = true
        binding.frameDetails.isVisible = true

        binding.frameDetails.onLaidOut {
            val alpha = ObjectAnimator.ofFloat(binding.frameDetails, View.ALPHA, 0.4f, 1f)
            val translate = ofFloat(binding.frameDetails, View.TRANSLATION_Y, binding.frameDetails.height * 0.3f, 0f)

            AnimatorSet().run {
                playTogether(alpha, translate)
                duration = ANIM_DURATION.toLong()
                interpolator = LinearOutSlowInInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        binding.frameMaster.isGone = true
                    }
                })
                start()
            }
        }
    }

    private fun animateOutFrameDetails() {
        binding.frameMaster.isVisible = true
        binding.frameDetails.onLaidOut {
            if (binding.frameDetails.isShown) {
                val alpha = ObjectAnimator.ofFloat(binding.frameDetails, View.ALPHA, 1f, 0f)
                val translate = ofFloat(binding.frameDetails, View.TRANSLATION_Y, 0f, binding.frameDetails.height * 0.3f)

                AnimatorSet().run {
                    playTogether(alpha, translate)
                    duration = ANIM_DURATION.toLong()
                    interpolator = FastOutLinearInInterpolator()
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            binding.frameDetails.alpha = 1f
                            binding.frameDetails.translationY = 0f
                            binding.frameDetails.isGone = true
                        }
                    })
                    start()
                }
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(STATE_SUPER, super.onSaveInstanceState())
        bundle.putString(STATE_CONTAINERS_STATE, state?.name)
        return bundle
    }

    public override fun onRestoreInstanceState(parcelable: Parcelable) {
        var superParcelable = parcelable
        if (parcelable is Bundle) {
            state = MainNavigator.State.valueOf(parcelable.getString(STATE_CONTAINERS_STATE)!!)
            superParcelable = parcelable.getParcelable(STATE_SUPER)!!
        }
        super.onRestoreInstanceState(superParcelable)
    }
}
