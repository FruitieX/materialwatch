package fruitiex.materialwatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;

import org.jraf.android.androidwearcolorpicker.app.ColorPickActivity;

public class WatchFaceConfig extends Activity implements WearableListView.ClickListener, WearableListView.OnScrollListener {

    public static String[] elements = { "Hands", "Ticks", "SecondHand", "AmbientColor", "InnerBG", "OuterBG", "SquareBG", "EnableTicks", "EnableShadows", "ResetSettings" };
    static Values val;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_face_config);

        val = new Values(getApplicationContext());

        // Get the list component from the layout of the activity
        WearableListView listView =
            (WearableListView) findViewById(R.id.wearable_list);

        // Assign an adapter to the list
        listView.setAdapter(new ListAdapter(this, elements));

        // Set a click listener
        listView.setClickListener(this);
    }

    // WearableListView click listener
    @Override
    public void onClick(WearableListView.ViewHolder v) {
        Integer tag = (Integer) v.itemView.getTag();
        if (elements[tag].equals("EnableTicks")) {
            val.setBoolean("EnableTicks", !val.getBoolean("EnableTicks"));
            finish();
        } else if (elements[tag].equals("EnableShadows")) {
            val.setBoolean("EnableShadows", !val.getBoolean("EnableShadows"));
            finish();
        } else if (elements[tag].equals("ResetSettings")) {
            val.resetValues();
            finish();
        } else {
            Intent intent = new ColorPickActivity.IntentBuilder().oldColor(val.getColor(elements[tag])).build(this);
            startActivityForResult(intent, tag);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            int pickedColor = ColorPickActivity.getPickedColor(data);
            val.setColor(elements[requestCode], pickedColor);
            WearableListView listView =
                (WearableListView) findViewById(R.id.wearable_list);
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onTopEmptyRegionClick() {}

    @Override
    public void onAbsoluteScrollChange(int scroll) {}

    @Override
    public void onScrollStateChanged(int scrollState) {}

    @Override
    public void onCentralPositionChanged(int centralPosition) {}

    @Override
    public void onScroll(int scroll) {}
}
