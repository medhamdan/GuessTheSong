package org.fairytail.guessthesong.db;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.fairytail.guessthesong.dagger.Daggered;

import javax.inject.Inject;

public class SongsCursorGetter extends Daggered {

    @Inject
    ContentResolver contentResolver;

    static final String[] SUPPORTED_MIME_TYPES = {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp3"),
            MimeTypeMap.getSingleton().getMimeTypeFromExtension("m4a"),
            MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp4"),
            MimeTypeMap.getSingleton().getMimeTypeFromExtension("aac")
    };

    private String supportedTypes = "('" + TextUtils.join("','", SUPPORTED_MIME_TYPES) + "')";

    private String selection = ""
            + MediaStore.Audio.Media.IS_MUSIC + " != 0"
            + " AND "
            + MediaStore.Audio.Media.MIME_TYPE + " in " + supportedTypes;

    private String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.MIME_TYPE
//            MediaStore.Audio.Media.TRACK,
//            MediaStore.Audio.Media.ALBUM_KEY,
//            MediaStore.Audio.Media.ALBUM_KEY,
    };

    public static final int _ID          = 0;
    public static final int TITLE        = 1;
    public static final int ARTIST       = 2;
    public static final int ALBUM        = 3;
    public static final int DURATION     = 4;
    public static final int DATA         = 5;
    public static final int DISPLAY_NAME = 6;
    public static final int SIZE         = 7;
    public static final int ALBUM_ID     = 8;
    public static final int ARTIST_ID    = 9;
//    public static final int TRACK        = 10;
//    public static final int ALBUM_KEY    = 11;
//    public static final int ALBUM_KEY    = 10;

    public Cursor getSongsCursor(Order order){
        String orderString = "";
        switch (order) {
            case ASCENDING:
                orderString = "ASC";
                break;
            case DESCENDING:
                orderString = "DESC";
                break;
            case RANDOM:
                orderString = "random()";
                break;
        }

        return contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                order == Order.RANDOM ? orderString :
                        MediaStore.Audio.Media.ARTIST + " "+orderString+", "
                        + MediaStore.Audio.Media.ALBUM_ID + " "+orderString+", "
                        + MediaStore.Audio.Media.TRACK + " "+orderString+", "
                        + MediaStore.Audio.Media.DISPLAY_NAME + " "+orderString
        );

    }

}
