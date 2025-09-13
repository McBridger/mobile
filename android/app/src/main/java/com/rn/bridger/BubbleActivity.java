package com.rn.bridger;

import android.os.Bundle;

import com.facebook.react.ReactActivity;
import javax.annotation.Nullable;

public class BubbleActivity extends ReactActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // "Регистрируем" эту Activity в нашем модуле
        BubbleModule.setBubbleActivityInstance(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // "Разрегистрируем" Activity, чтобы избежать утечек
        if (BubbleModule.bubbleActivityInstance != null && BubbleModule.bubbleActivityInstance.get() == this) {
            BubbleModule.setBubbleActivityInstance(null);
        }
    }

    /**
     * Returns the name of the main component registered from JavaScript.
     * This is used to schedule rendering of the component.
     *
     * ВОТ КЛЮЧЕВАЯ СВЯЗЬ: это имя должно совпадать с именем,
     * которое вы использовали в AppRegistry.registerComponent()
     */
    @Nullable
    @Override
    protected String getMainComponentName() {
        return "BubbleScreen";
    }
}