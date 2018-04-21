package com.fsmytsai.aiclock.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.RelativeLayout
import com.fsmytsai.aiclock.model.XY
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
        val rlParent = parent as RelativeLayout?
        if (rlParent != null)
            mBlockHeight = rlParent.height / 4
        else
            SharedService.writeDebugLog(context, "$mTag rlParent is null")
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        mOriginX = x
        mOriginY = y
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (mIsAnimating)
            return false

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                setScreenWidth()
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

                val centerY = y + height / 2
                when (centerY) {
                    in -1000 until mBlockHeight -> mMyDragListener?.dragging(3)
                    in mBlockHeight until mBlockHeight * 2 -> mMyDragListener?.dragging(2)
                    in mBlockHeight * 2 until mBlockHeight * 3 -> mMyDragListener?.dragging(1)
                    else -> mMyDragListener?.dragging(0)
                }
            }
            MotionEvent.ACTION_UP -> {
                mIsAnimating = true
                val centerY = y + height / 2
                when (centerY) {
                    in -1000 until mBlockHeight -> mMyDragListener?.up(3)
                    in mBlockHeight until mBlockHeight * 2 -> mMyDragListener?.up(2)
                    in mBlockHeight * 2 until mBlockHeight * 3 -> mMyDragListener?.up(1)
                    else -> mMyDragListener?.up(0)
                }

//                animate().x(mOriginX)
//                        .y(mOriginY)
//                        .setDuration(300)
//                        .setListener(object : AnimatorListenerAdapter() {
//                            override fun onAnimationEnd(animation: Animator?) {
//                                super.onAnimationEnd(animation)
//                                mIsAnimating = false
//                                mMyDragListener?.finished()
//                            }
//                        })
                val start = XY(x.toInt(), y.toInt())
                val end = XY(mOriginX.toInt(), mOriginY.toInt())
                val valueAnimator = ValueAnimator.ofObject(XYEvaluator(), start, end)
                valueAnimator.addUpdateListener { animation ->
                    val xy = animation.animatedValue as XY
                    layout(xy.x, xy.y, xy.x + width, xy.y + height)
                }
                valueAnimator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mIsAnimating = false
                        mMyDragListener?.finished()
                    }
                })
                valueAnimator.duration = 300
                valueAnimator.start()
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

    private class XYEvaluator : TypeEvaluator<XY> {
        override fun evaluate(fraction: Float, startValue: XY, endValue: XY): XY {
            val x = startValue.x + fraction * (endValue.x - startValue.x)
            val y = startValue.y + fraction * (endValue.y - startValue.y)
            return XY(x.toInt(), y.toInt())
        }

    }
}