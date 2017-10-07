package vladimir.apps.dwts.BTDisplay;

import android.widget.TextView;

/**
 * This takes care of the synchronized access to the displayed strings on the display
 * @author
 *      Vladimir (jelezarov.vladimir@gmail.com)
 */
class SyncTextView {
    private TextView syncView;

    SyncTextView(TextView view) {
            this.syncView = view;
    }

    private synchronized TextView getSyncView () {
        return syncView;
    }

    String getText() {
        return getSyncView().getText().toString();
    }

    void setText(CharSequence text) {
        getSyncView().setText(text);
    }
}
