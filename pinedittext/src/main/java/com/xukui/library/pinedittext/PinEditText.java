package com.xukui.library.pinedittext;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;

public class PinEditText extends AppCompatEditText {

    private static final String XML_NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";

    public static final String DEFAULT_MASK = "\u25CF";

    protected int mAnimatedType;//动画类型
    protected String mPinMask;//遮罩, 用于密码, 保护内容安全
    protected StringBuilder mPinMaskBuilder;
    protected String mPinHint;//提示
    protected Drawable mPinBackgroundDrawable;//字背景图


    protected float mSpace = 24; //24 dp by default, space between the lines
    protected float mCharSize;
    protected float mNumChars = 4;
    protected float mTextBottomPadding = 8; //8dp by default, height of the text from our lines
    protected int mMaxLength = 4;
    protected RectF[] mLineCoords;
    protected float[] mCharBottom;
    protected Paint mCharPaint;
    protected Paint mLastCharPaint;
    protected Paint mSingleCharPaint;
    protected Rect mTextHeight = new Rect();


    protected OnClickListener mClickListener;
    protected OnPinEnteredListener mOnPinEnteredListener = null;

    protected float mLineStroke = 1; //1dp by default
    protected float mLineStrokeSelected = 2; //2dp by default
    protected Paint mLinesPaint;
    protected boolean mAnimate = false;
    protected boolean mHasError = false;
    protected ColorStateList mOriginalTextColors;
    protected int[][] mStates = new int[][]{
            new int[]{android.R.attr.state_selected}, // selected
            new int[]{android.R.attr.state_active}, // error
            new int[]{android.R.attr.state_focused}, // focused
            new int[]{-android.R.attr.state_focused}, // unfocused
    };

    protected int[] mColors = new int[]{
            Color.GREEN,
            Color.RED,
            Color.BLACK,
            Color.GRAY
    };

    protected ColorStateList mColorStates = new ColorStateList(mStates, mColors);

    public PinEditText(Context context) {
        super(context);
        initData(context, null, 0);
        initView(context);
    }

    public PinEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initData(context, attrs, 0);
        initView(context);
    }

    public PinEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initData(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initData(Context context, AttributeSet attrs, int defStyleAttr) {
        float multi = context.getResources().getDisplayMetrics().density;
        mLineStroke = multi * mLineStroke;
        mLineStrokeSelected = multi * mLineStrokeSelected;
        mSpace = multi * mSpace; //convert to pixels for our density
        mTextBottomPadding = multi * mTextBottomPadding; //convert to pixels for our density

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PinEditText, defStyleAttr, 0);

            TypedValue animatedTypeValue = new TypedValue();
            ta.getValue(R.styleable.PinEditText_pet_animation_type, animatedTypeValue);
            mAnimatedType = animatedTypeValue.data;
            mPinMask = ta.getString(R.styleable.PinEditText_pet_pin_mask);
            mPinHint = ta.getString(R.styleable.PinEditText_pet_pin_hint);
            mPinBackgroundDrawable = ta.getDrawable(R.styleable.PinEditText_pet_pin_background_drawable);


            mLineStroke = ta.getDimension(R.styleable.PinEditText_pin_line_stroke, mLineStroke);
            mLineStrokeSelected = ta.getDimension(R.styleable.PinEditText_pin_line_stroke_selected, mLineStrokeSelected);
            mSpace = ta.getDimension(R.styleable.PinEditText_pin_character_spacing, mSpace);
            mTextBottomPadding = ta.getDimension(R.styleable.PinEditText_pin_text_bottom_padding, mTextBottomPadding);


            ColorStateList colors = ta.getColorStateList(R.styleable.PinEditText_pin_line_colors);
            if (colors != null) {
                mColorStates = colors;
            }

            ta.recycle();
        }

        if (isPassword() && mPinMask == null) {
            mPinMask = DEFAULT_MASK;
        }

        mCharPaint = new Paint(getPaint());
        mLastCharPaint = new Paint(getPaint());
        mSingleCharPaint = new Paint(getPaint());
        mLinesPaint = new Paint(getPaint());
        mLinesPaint.setStrokeWidth(mLineStroke);

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorControlActivated,
                outValue, true);
        int colorSelected = outValue.data;
        mColors[0] = colorSelected;

        int colorFocused = isInEditMode() ? Color.GRAY : Color.parseColor("#6B767E");
        mColors[1] = colorFocused;

        int colorUnfocused = isInEditMode() ? Color.GRAY : Color.parseColor("#6B767E");
        mColors[2] = colorUnfocused;

        setBackgroundResource(0);

        mMaxLength = attrs.getAttributeIntValue(XML_NAMESPACE_ANDROID, "maxLength", 4);
        mNumChars = mMaxLength;

        //Disable copy paste
        super.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });
        // When tapped, move cursor to end of text.
        super.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelection(getText().length());
                if (mClickListener != null) {
                    mClickListener.onClick(v);
                }
            }
        });

        super.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setSelection(getText().length());
                return true;
            }
        });

        if (!TextUtils.isEmpty(mPinMask)) {
            mPinMaskBuilder = getMaskChars();
        }

        //Height of the characters, used if there is a background drawable
        getPaint().getTextBounds("|", 0, 1, mTextHeight);

        mAnimate = mAnimatedType > -1;
    }

    private void initView(Context context) {
    }

    public void setMaxLength(final int maxLength) {
        mMaxLength = maxLength;
        mNumChars = maxLength;

        setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});

        setText(null);
        invalidate();
    }

    public void setMask(String mask) {
        mPinMask = mask;
        mPinMaskBuilder = null;
        invalidate();
    }

    public void setCharHint(String hint) {
        mPinHint = hint;
        invalidate();
    }

    @Override
    public void setInputType(int type) {
        super.setInputType(type);
        if (isPassword() && mPinMask == null) {
            setMask(DEFAULT_MASK);

        } else {
            setMask(null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mOriginalTextColors = getTextColors();
        if (mOriginalTextColors != null) {
            mLastCharPaint.setColor(mOriginalTextColors.getDefaultColor());
            mCharPaint.setColor(mOriginalTextColors.getDefaultColor());
            mSingleCharPaint.setColor(getCurrentHintTextColor());
        }
        int availableWidth = getWidth() - ViewCompat.getPaddingEnd(this) - ViewCompat.getPaddingStart(this);
        if (mSpace < 0) {
            mCharSize = (availableWidth / (mNumChars * 2 - 1));
        } else {
            mCharSize = (availableWidth - (mSpace * (mNumChars - 1))) / mNumChars;
        }
        mLineCoords = new RectF[(int) mNumChars];
        mCharBottom = new float[(int) mNumChars];
        int startX;
        int bottom = getHeight() - getPaddingBottom();
        int rtlFlag;
        final boolean isLayoutRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
        if (isLayoutRtl) {
            rtlFlag = -1;
            startX = (int) (getWidth() - ViewCompat.getPaddingStart(this) - mCharSize);
        } else {
            rtlFlag = 1;
            startX = ViewCompat.getPaddingStart(this);
        }
        for (int i = 0; i < mNumChars; i++) {
            mLineCoords[i] = new RectF(startX, bottom, startX + mCharSize, bottom);
            if (mPinBackgroundDrawable != null) {
                mLineCoords[i].top = getPaddingTop();
                mLineCoords[i].right = startX + mLineCoords[i].width();
            }

            if (mSpace < 0) {
                startX += rtlFlag * mCharSize * 2;
            } else {
                startX += rtlFlag * (mCharSize + mSpace);
            }
            mCharBottom[i] = mLineCoords[i].bottom - mTextBottomPadding;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int measuredWidth = 0;
        int measuredHeight = 0;
        // If we want a square or circle pin box, we might be able
        // to figure out the dimensions outselves
        // if width and height are set to wrap_content or match_parent
        if (widthMode == MeasureSpec.EXACTLY) {
            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
            measuredHeight = (int) ((measuredWidth - (mNumChars - 1 * mSpace)) / mNumChars);
        } else if (heightMode == MeasureSpec.EXACTLY) {
            measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
            measuredWidth = (int) ((measuredHeight * mNumChars) + (mSpace * mNumChars - 1));
        } else if (widthMode == MeasureSpec.AT_MOST) {
            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
            measuredHeight = (int) ((measuredWidth - (mNumChars - 1 * mSpace)) / mNumChars);
        } else if (heightMode == MeasureSpec.AT_MOST) {
            measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
            measuredWidth = (int) ((measuredHeight * mNumChars) + (mSpace * mNumChars - 1));
        } else {
            // Both unspecific
            // Try for a width based on our minimum
            measuredWidth = getPaddingLeft() + getPaddingRight() + getSuggestedMinimumWidth();

            // Whatever the width ends up being, ask for a height that would let the pie
            // get as big as it can
            measuredHeight = (int) ((measuredWidth - (mNumChars - 1 * mSpace)) / mNumChars);
        }

        setMeasuredDimension(
                resolveSizeAndState(measuredWidth, widthMeasureSpec, 1), resolveSizeAndState(measuredHeight, heightMeasureSpec, 0));
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
    }

    @Override
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        throw new RuntimeException("setCustomSelectionActionModeCallback() not supported.");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);
        CharSequence text = getFullText();
        int textLength = text.length();
        float[] textWidths = new float[textLength];
        getPaint().getTextWidths(text, 0, textLength, textWidths);

        float hintWidth = 0;
        if (mPinHint != null) {
            float[] hintWidths = new float[mPinHint.length()];
            getPaint().getTextWidths(mPinHint, hintWidths);
            for (float i : hintWidths) {
                hintWidth += i;
            }
        }
        for (int i = 0; i < mNumChars; i++) {
            //If a background for the pin characters is specified, it should be behind the characters.
            if (mPinBackgroundDrawable != null) {
                updateDrawableState(i < textLength, i == textLength);
                mPinBackgroundDrawable.setBounds((int) mLineCoords[i].left, (int) mLineCoords[i].top, (int) mLineCoords[i].right, (int) mLineCoords[i].bottom);
                mPinBackgroundDrawable.draw(canvas);
            }
            float middle = mLineCoords[i].left + mCharSize / 2;
            if (textLength > i) {
                if (!mAnimate || i != textLength - 1) {
                    canvas.drawText(text, i, i + 1, middle - textWidths[i] / 2, mCharBottom[i], mCharPaint);
                } else {
                    canvas.drawText(text, i, i + 1, middle - textWidths[i] / 2, mCharBottom[i], mLastCharPaint);
                }
            } else if (mPinHint != null) {
                canvas.drawText(mPinHint, middle - hintWidth / 2, mCharBottom[i], mSingleCharPaint);
            }
            //The lines should be in front of the text (because that's how I want it).
            if (mPinBackgroundDrawable == null) {
                updateColorForLines(i <= textLength);
                canvas.drawLine(mLineCoords[i].left, mLineCoords[i].top, mLineCoords[i].right, mLineCoords[i].bottom, mLinesPaint);
            }
        }
    }

    private boolean isPassword() {
        if ((getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            return true;

        } else if ((getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
            return true;

        } else {
            return false;
        }
    }

    private CharSequence getFullText() {
        if (TextUtils.isEmpty(mPinMask)) {
            return getText();

        } else {
            return getMaskChars();
        }
    }

    private StringBuilder getMaskChars() {
        if (mPinMaskBuilder == null) {
            mPinMaskBuilder = new StringBuilder();
        }

        int length = getText().length();

        while (mPinMaskBuilder.length() != length) {
            if (mPinMaskBuilder.length() < length) {
                mPinMaskBuilder.append(mPinMask);

            } else {
                mPinMaskBuilder.deleteCharAt(mPinMaskBuilder.length() - 1);
            }
        }

        return mPinMaskBuilder;
    }

    private int getColorForState(int... states) {
        return mColorStates.getColorForState(states, Color.GRAY);
    }

    /**
     * @param hasTextOrIsNext Is the color for a character that has been typed or is
     *                        the next character to be typed?
     */
    protected void updateColorForLines(boolean hasTextOrIsNext) {
        if (mHasError) {
            mLinesPaint.setColor(getColorForState(android.R.attr.state_active));
        } else if (isFocused()) {
            mLinesPaint.setStrokeWidth(mLineStrokeSelected);
            mLinesPaint.setColor(getColorForState(android.R.attr.state_focused));
            if (hasTextOrIsNext) {
                mLinesPaint.setColor(getColorForState(android.R.attr.state_selected));
            }
        } else {
            mLinesPaint.setStrokeWidth(mLineStroke);
            mLinesPaint.setColor(getColorForState(-android.R.attr.state_focused));
        }
    }

    protected void updateDrawableState(boolean hasText, boolean isNext) {
        if (mHasError) {
            mPinBackgroundDrawable.setState(new int[]{android.R.attr.state_active});
        } else if (isFocused()) {
            mPinBackgroundDrawable.setState(new int[]{android.R.attr.state_focused});
            if (isNext) {
                mPinBackgroundDrawable.setState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected});
            } else if (hasText) {
                mPinBackgroundDrawable.setState(new int[]{android.R.attr.state_focused, android.R.attr.state_checked});
            }
        } else {
            if (hasText) {
                mPinBackgroundDrawable.setState(new int[]{-android.R.attr.state_focused, android.R.attr.state_checked});
            } else {
                mPinBackgroundDrawable.setState(new int[]{-android.R.attr.state_focused});
            }
        }
    }

    public void setError(boolean hasError) {
        mHasError = hasError;
        invalidate();
    }

    public boolean isError() {
        return mHasError;
    }

    /**
     * Request focus on this PinEditText
     */
    public void focus() {
        requestFocus();

        // Show keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(this, 0);
    }

    @Override
    public void setTypeface(@Nullable Typeface tf) {
        super.setTypeface(tf);
        setCustomTypeface(tf);
    }

    @Override
    public void setTypeface(@Nullable Typeface tf, int style) {
        super.setTypeface(tf, style);
        setCustomTypeface(tf);
    }

    private void setCustomTypeface(@Nullable Typeface tf) {
        if (mCharPaint != null) {
            mCharPaint.setTypeface(tf);
            mLastCharPaint.setTypeface(tf);
            mSingleCharPaint.setTypeface(tf);
            mLinesPaint.setTypeface(tf);
        }
    }

    public void setPinLineColors(ColorStateList colors) {
        mColorStates = colors;
        invalidate();
    }

    public void setPinBackground(Drawable pinBackground) {
        mPinBackgroundDrawable = pinBackground;
        invalidate();
    }

    @Override
    protected void onTextChanged(CharSequence text, final int start, int lengthBefore, final int lengthAfter) {
        setError(false);
        if (mLineCoords == null || !mAnimate) {
            if (mOnPinEnteredListener != null && text.length() == mMaxLength) {
                mOnPinEnteredListener.onPinEntered(text);
            }
            return;
        }

        if (mAnimatedType == -1) {
            invalidate();
            return;
        }

        if (lengthAfter > lengthBefore) {
            if (mAnimatedType == 0) {
                animatePopIn();

            } else {
                animateBottomUp(text, start);
            }
        }
    }

    private void animatePopIn() {
        ValueAnimator va = ValueAnimator.ofFloat(1, getPaint().getTextSize());
        va.setDuration(200);
        va.setInterpolator(new OvershootInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mLastCharPaint.setTextSize((Float) animation.getAnimatedValue());
                PinEditText.this.invalidate();
            }

        });
        if (getText().length() == mMaxLength && mOnPinEnteredListener != null) {
            va.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mOnPinEnteredListener.onPinEntered(getText());
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }
        va.start();
    }

    private void animateBottomUp(CharSequence text, final int start) {
        mCharBottom[start] = mLineCoords[start].bottom - mTextBottomPadding;
        ValueAnimator animUp = ValueAnimator.ofFloat(mCharBottom[start] + getPaint().getTextSize(), mCharBottom[start]);
        animUp.setDuration(300);
        animUp.setInterpolator(new OvershootInterpolator());
        animUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float value = (Float) animation.getAnimatedValue();
                mCharBottom[start] = value;
                PinEditText.this.invalidate();
            }

        });

        mLastCharPaint.setAlpha(255);
        ValueAnimator animAlpha = ValueAnimator.ofInt(0, 255);
        animAlpha.setDuration(300);
        animAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                mLastCharPaint.setAlpha(value);
            }

        });

        AnimatorSet set = new AnimatorSet();
        if (text.length() == mMaxLength && mOnPinEnteredListener != null) {
            set.addListener(new Animator.AnimatorListener() {

                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mOnPinEnteredListener.onPinEntered(getText());
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }
        set.playTogether(animUp, animAlpha);
        set.start();
    }

    public void setAnimateText(boolean animate) {
        mAnimate = animate;
    }

    public void setOnPinEnteredListener(OnPinEnteredListener l) {
        mOnPinEnteredListener = l;
    }

    public interface OnPinEnteredListener {

        void onPinEntered(CharSequence str);

    }

}