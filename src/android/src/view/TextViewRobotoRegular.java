package ru.simdev.livetex.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * Created by user on 21.09.15.
 */
public class TextViewRobotoRegular extends AppCompatTextView {

    public TextViewRobotoRegular(Context context) {
        super(context);
    }

    public TextViewRobotoRegular(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        Typeface myTypeface = Typeface.createFromAsset(context.getAssets(), "www/assets/fonts/Montserrat/Montserrat-Regular.ttf");
        setTypeface(myTypeface);
    }
}
