package pl.synth.pinry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.Tag;
import android.util.Log;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpCookie;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

class NetworkClient {
    private static final String TAG = "NetworkClient";
    private Context context;
    private static final int HTTP_REQUEST_TIMEOUT_MS = 30 * 1000;
    private String baseUrl;

    private static HttpClient getHttpClient() {
        HttpClient client = new DefaultHttpClient();
        final HttpParams params = client.getParams();
        HttpConnectionParams.setConnectionTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
        HttpConnectionParams.setSoTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
        ConnManagerParams.setTimeout(params, HTTP_REQUEST_TIMEOUT_MS);

        return client;
    }

    public NetworkClient(String url, Context context) {
        this.context = context;
        this.baseUrl = url;
    }

    /**
     * Connects to the Pinry server and authenticates using given credentials
     * @param username The pinry username
     * @param password The pinry password
     * @return whether authentication was successful
     */
    public boolean authenticate(String username, String password) {
        return false;
    }

    public ArrayList<Pin> getPinsSince(Long epoch) {
        ArrayList<Pin> returnList = new ArrayList<Pin>();
        HttpClient client = new DefaultHttpClient();
        String url = baseUrl;
        url += "/api/pin";

        Log.i(TAG, "getPinsSince " + epoch);

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("format", "json"));
        String paramString = URLEncodedUtils.format(params, "utf-8");
        url += "?" + paramString;

        HttpGet request = new HttpGet(url);
        JSONObject json;
        try {
            HttpResponse httpResponse = client.execute(request);
            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                return returnList;
            }

            String responseString = EntityUtils.toString(httpResponse.getEntity(), HTTP.UTF_8);
            json = new JSONObject(responseString);
        } catch (IOException e) {
            return returnList;
        } catch (JSONException e) {
            return returnList;
        }

        if(!json.has("meta")) {
            return null;
        }

        try {
            int totalCount = json.getJSONObject("meta").getInt("total_count");
            JSONArray objects = json.getJSONArray("objects");

            for(int i = 0; i < totalCount; i++) {
                JSONObject object = objects.getJSONObject(i);
                String imagePath = object.getString("image");
                String thumbnailPath;
                String localPath;
                int pinId = object.getInt("id");
                try {
                    localPath = fetchImage(imagePath, pinId);
                    thumbnailPath = processImage(localPath);
                } catch (IOException e) {
                    Log.e(TAG, "fetchAndProcessImage failed: " + e.getMessage());
                    continue;
                }

                String description = object.getString("description");
                String sourceUrl = object.getString("url");
                int id = object.getInt("id");
                long publishedDate = 0L;

                Pin pin = new Pin(id, sourceUrl, localPath, thumbnailPath, description, url, publishedDate);

                returnList.add(pin);
            }
        } catch (JSONException e) {
            return null;
        }

        return returnList;
    }

    private String processImage(String localPath) {
        String fileName = last(localPath.split("/"));
        File thumbnailPath = context.getExternalFilesDir("thumbnails");
        File thumbnailFile = new File(thumbnailPath, fileName);

        if (thumbnailFile.exists()) {
            return thumbnailFile.getAbsolutePath();
        }

        final int IMAGE_MAX_SIZE = 200000;
        /* first just get the image size */
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(localPath, opts);


        int scale = 1;
        while ((opts.outWidth * opts.outHeight) * (1 / Math.pow(scale, 2)) > IMAGE_MAX_SIZE) {
                scale *= 2;
        }

        Bitmap thumbnail;
        if (scale > 1) {
            scale--;
            opts = new BitmapFactory.Options();
            opts.inSampleSize = scale;
            thumbnail = BitmapFactory.decodeFile(localPath, opts);

            int width = thumbnail.getWidth();
            int height = thumbnail.getHeight();

            double y = Math.sqrt(IMAGE_MAX_SIZE / (((double) width) / height));
            double x = (y / height) * width;

            thumbnail = Bitmap.createScaledBitmap(thumbnail, (int) x, (int) y, true);

            Bitmap.CompressFormat compress;
            compress = Bitmap.CompressFormat.JPEG;

            if(opts.outMimeType == "image/png") {
                compress = Bitmap.CompressFormat.PNG;
            } else if (opts.outMimeType == "image/jpeg") {
                compress = Bitmap.CompressFormat.JPEG;
            }

            try {
                OutputStream out = new FileOutputStream(thumbnailFile.getAbsolutePath());
                thumbnail.compress(compress, 90, out);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Could not save thumbnail: "+ e.getMessage());
                return null;
            }
            return thumbnailFile.getAbsolutePath();
        }

        return localPath;
    }

    private static <T> T last(T[] array) {
        return array[array.length - 1];
    }

    private String fetchImage(String imagePath, int pinId) throws IOException {
        HttpClient client = getHttpClient();

        String remoteFileName = last(imagePath.split("/"));
        File path = context.getExternalFilesDir(null);

        String[] tokens = remoteFileName.split("\\.(?=[^\\.]+$)");
        File localFile = new File(path, tokens[0] + "_" + pinId + "." + tokens[1]);

        if (localFile.exists()) {
            return localFile.getAbsolutePath();
        }

        String imageUrl = baseUrl + imagePath;
        HttpGet request = new HttpGet(imageUrl);
        OutputStream stream = null;
        try {
            HttpResponse response = client.execute(request);
            stream = new FileOutputStream(localFile);
            response.getEntity().writeTo(stream);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        return localFile.getAbsolutePath();
    }
}
