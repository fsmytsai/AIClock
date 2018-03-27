package com.fsmytsai.aiclock.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.widget.ImageView
import com.fsmytsai.aiclock.service.app.SharedService


class DragImageView : ImageView {
    private val mTag = "DragImageView"
    private var mBlockHeight = 0
    private var mOriginX = 0f
    private var mOriginY = 0f
    private var mDownX = 0f
    private var mDownY = 0f
    private var mIsAnimating = false
    private var mMyDragListener: MyDragListener? = null

    constructor(context: Context) : super(context) {
        setScreenWidth()
        adjustViewBounds = true
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setScreenWidth()
        adjustViewBounds = true
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        setScreenWidth()
        adjustViewBounds = true
    }

    private fun setScreenWidth() {
        if (context is Activity) {
            val dm = DisplayMetrics()
            val activity = context as Activity
            activity.windowManager.defaultDisplay.getMetrics(dm)
            mBlockHeight = dm.heightPixels / 4
        } else
            SharedService.writeDebugLog(context, "$mTag context not an activity")
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        mOriginX = x
        mOriginY = y
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (mBlockHeight == 0 || mIsAnimating)
            return false

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = event.x
                mDownY = event.y
                mMyDragListener?.down()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - mDownX
                val dy = event.y - mDownY
                val l = left + dx.toInt()
                val r = right + dx.toInt()
                val t = top + dy.toInt()
                val b = bottom + dy.toInt()
                layout(l, t, r, b)

                val centerY = y - height / 2
                when (centerY) {
                    in -1000 until mBlockHeight -> {
                        //沒事
                        mMyDragListener?.dragging(3)
                    }
                    in mBlockHeight until mBlockHeight * 2 -> {
                        //5分鐘
                        mMyDragListener?.dragging(2)
                    }
                    in mBlockHeight * 2 until mBlockHeight * 3 -> {
                        //10分鐘
                        mMyDragListener?.dragging(1)
                    }
                    else -> {
                        //關閉
                        mMyDragListener?.dragging(0)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val centerY = y - height / 2
                mIsAnimating = true
                when (centerY) {
                    in -1000 until mBlockHeight -> {
                        //沒事
                        mMyDragListener?.up(3)
                    }
                    in mBlockHeight until mBlockHeight * 2 -> {
                        //5分鐘
                        mMyDragListener?.up(2)
                    }
                    in mBlockHeight * 2 until mBlockHeight * 3 -> {
                        //10分鐘
                        mMyDragListener?.up(1)
                    }
                    else -> {
                        //關閉
                        mMyDragListener?.up(0)
                    }
                }

                animate().x(mOriginX)
                        .y(mOriginY)
                        .setDuration(300)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                super.onAnimationEnd(animation)
                                mIsAnimating = false
                                mMyDragListener?.finished()
                            }
                        })
            }
        }
        return true
    }

    fun setMyDragListener(myDragListener: MyDragListener) {
        mMyDragListener = myDragListener
    }

    interface MyDragListener {
        fun down()
        fun up(type: Int)
        fun finished()
        fun dragging(type: Int)
    }
}