package pl.synth.pinry;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class PinSyncAdapterService extends Service {
    private static final String TAG = "PinSyncAdapterService";
    private static SyncAdapterImpl syncAdapter = null;

    @Override
    public IBinder onBind(Intent intent) {
        return getSyncAdapter().getSyncAdapterBinder();
    }

    private static class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
        private Context context;

        public SyncAdapterImpl(Context context, boolean autoInitialize) {
            super(context, autoInitialize);
            this.context = context;
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            try {
                PinSyncAdapterService.performSync(context, account, extras, authority, provider, syncResult);
            } catch (OperationCanceledException e) {

            }
        }
    }

    private SyncAdapterImpl getSyncAdapter() {
        if (syncAdapter == null) {
            syncAdapter = new SyncAdapterImpl(this, true);
        }
        return syncAdapter;
    }

    private static void performSync(Context context, Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) throws OperationCanceledException {
        ContentResolver contentResolver = context.getContentResolver();
        AccountManager manager = AccountManager.get(context);
        String url = manager.getUserData(account, "url");
        Log.i(TAG, "performSync: " + account.toString() + " (url: " + url + ")");
        NetworkClient client = new NetworkClient(url, context);
        ArrayList<Pin> newPins = client.getPinsSince(0L);

        ContentValues values = new ContentValues();

        for(Pin pin : newPins) {
            values.clear();

            Uri uri = ContentUris.withAppendedId(Pinry.Pins.CONTENT_ID_URI_BASE, pin.getId());
            Cursor c = contentResolver.query(uri, new String[] {Pinry.Pins._ID, Pinry.Pins.COLUMN_NAME_IMAGE_PATH}, null, null, null);
            if (c.getCount() > 0) {
                c.moveToFirst();
                c.close();
                continue;
            }

            values.put(Pinry.Pins._ID, pin.getId());
            values.put(Pinry.Pins.COLUMN_NAME_IMAGE_PATH, pin.getLocalPath());
            values.put(Pinry.Pins.COLUMN_NAME_THUMBNAIL_PATH, pin.getThumbnailPath());
            values.put(Pinry.Pins.COLUMN_NAME_DESCRIPTION, pin.getDescription());
            values.put(Pinry.Pins.COLUMN_NAME_SYNC_STATE, Pinry.Pins.SyncState.SYNCED);
            values.put(Pinry.Pins.COLUMN_NAME_SOURCE_URL, pin.getSourceUrl());
            values.put(Pinry.Pins.COLUMN_NAME_PUBLISHED, pin.getPublishedDate());

            uri = contentResolver.insert(Pinry.Pins.CONTENT_URI, values);
        }
    }
}
