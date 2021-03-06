package lv.kasparsj.android.dwob;

import java.util.Calendar;

import lv.kasparsj.android.dwob.R;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.TextView;

public class DwobWidget extends AppWidgetProvider {
	
    private ScreenStateReceiver screenStateReceiver = new ScreenStateReceiver();
    private DwobUpdateReceiver screenUpdateReceiver = new DwobUpdateReceiver();
	
	private PendingIntent createUpdateIntent(Context context) {
		Resources r = context.getResources();
	    Intent intent = new Intent(r.getString(R.string.action_update));
	    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	    return pendingIntent;
	}
	
	@Override
	public void onEnabled(Context context) {
		Log.i("test", "DwobWidget::onEnabled");
		
		IntentFilter stateFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		stateFilter.addAction(Intent.ACTION_SCREEN_ON);
    	context.getApplicationContext().registerReceiver(screenStateReceiver, stateFilter);
    	
		IntentFilter updateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    	context.getApplicationContext().registerReceiver(screenUpdateReceiver, updateFilter);
		
    	Resources r = context.getResources();
		AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        alarmManager.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), r.getInteger(R.integer.update_period), createUpdateIntent(context));
	}
	
	public void onDisabled(Context context) {
		Log.i("test", "DwobWidget::onDisabled");
		
		AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(createUpdateIntent(context));
        
		try {
			context.getApplicationContext().unregisterReceiver(screenStateReceiver);
		} catch (RuntimeException e) {
			// do nothing
		}
		try {
			context.getApplicationContext().unregisterReceiver(screenUpdateReceiver);
		} catch (RuntimeException e) {
			// do nothing
		}
	}
    
    private float getDefaultTextSize(int numLines) {
        switch (numLines) {
	    	case 1:
	    	case 2:
	    	case 3:
	    	case 4:
	    		return 16;
	    	case 5:
	    		return 13;
	    	case 6:
	    		return 11;
	    	case 7:
	    		return 9;
    	}
        return 8;
    }
    
    private int countTextViewLines(TextView textView, String[] lines, float lineWidth) {
    	int numLines = lines.length;
    	for (int i=0; i<lines.length; i++) {
        	if (textView.getPaint().measureText(lines[i]) > lineWidth) {
        		numLines++;
        	}
        }
    	return numLines;
    }
    
    private int getLinesVisible(float textSize) {
    	if (textSize > 13) return 4;
    	else if (textSize > 11) return 5;
    	else if (textSize > 9) return 6;
    	else if (textSize > 8) return 7;
    	return 8;
    }
    
    public void onReceive(Context context, Intent intent) {
    	Log.i("test", "DwobWidget::onReceive ("+intent.getAction()+")");
    	
    	DwobApp app = ((DwobApp) context.getApplicationContext());
    	Resources r = context.getResources();
    	if (intent.getAction().equals(r.getString(R.string.action_update)) && app.isOutdated()) {
    		// don't update if screen is off
    		if (ScreenStateReceiver.screenOff) {
    			DwobUpdateReceiver.pendingUpdate = true;
    		}
    		else {
    			app.update();
    		}
    	}
    	
    	Object[] translation = app.getTranslation().toArray();
    	if (intent.getAction().equals(r.getString(R.string.action_refresh)) || translation.length > 0) {
			// Retrieve latest translation
            String text = r.getString(R.string.widget_error);
            if (translation.length > 0) {
            	String html = TextUtils.join("\n<br />\n", translation).trim().replaceAll("^<br />", "").trim();
            	text = Html.fromHtml(html).toString();
            }
            // Detect numLines to display
            String[] lines = text.split("\r\n|\r|\n");
            int numLines = lines.length;
            if (numLines > 6 && text.indexOf("\n\n") > -1) {
            	String[] parts = text.split("\n\n");
            	text = parts[0].trim()+"...";
            	lines = text.split("\r\n|\r|\n");
            	numLines = lines.length;
            }
            // Measure text width, and alter numLines accordingly
            TextView textView = new TextView(context);
            textView.setTextSize(getDefaultTextSize(numLines));
            float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, r.getDimension(R.dimen.widget_padding), r.getDisplayMetrics());
            float margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, r.getDimension(R.dimen.widget_margin), r.getDisplayMetrics());
            float lineWidth = (r.getDisplayMetrics().widthPixels - padding*2 - margin*2);
            while (numLines < countTextViewLines(textView, lines, lineWidth)) {
            	textView.setTextSize((textView.getTextSize()-.5f));
            	numLines = getLinesVisible(textView.getTextSize());
            }
            // Build an update that holds the updated widget contents
            RemoteViews updateViews;
            updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_words);
            updateViews.setTextViewText(R.id.words, text);
            updateViews.setFloat(R.id.words, "setTextSize", textView.getTextSize());
            // setOnClickPendingIntent
            Intent defineIntent = new Intent(context, DwobActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, defineIntent, 0);
            updateViews.setOnClickPendingIntent(R.id.words, pendingIntent);
			// update Widget
			ComponentName thisWidget = new ComponentName(context, DwobWidget.class);
	        AppWidgetManager manager = AppWidgetManager.getInstance(context);
	        manager.updateAppWidget(thisWidget, updateViews);
    	}
    	
		super.onReceive(context, intent);
	}
}
