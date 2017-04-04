package ru.vinyarsky.englishmedia.db;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;


public final class Podcast {

    public static final String _ID = "_id";
    public static final String CODE = "code";
    public static final String LEVEL = "level";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String IMAGE_PATH = "image_path";
    public static final String RSS_URL = "rss_url";
    public static final String SUBSCRIBED = "subscribed";

    private static String TABLE_NAME = "Podcasts";

    private static final String SQL_CREATE_TABLE =
            String.format("create table %s (", TABLE_NAME) +
            String.format("  %s integer primary key autoincrement not null, ", _ID) +
            String.format("  %s text not null, ", CODE) +
            String.format("  %s integer not null, ", LEVEL) +
            String.format("  %s text not null, ", TITLE) +
            String.format("  %s text, ", DESCRIPTION) +
            String.format("  %s text, ", IMAGE_PATH) +
            String.format("  %s text not null, ", RSS_URL) +
            String.format("  %s integer not null)", SUBSCRIBED);
    private static final String SQL_SELECT_ALL = String.format("select * from %s", TABLE_NAME);
    private static final String SQL_SELECT_BY_ID = String.format("select * from %s where %s = ?0", TABLE_NAME, _ID);

    private long id;
    private UUID code;
    private PodcastLevel level;
    private String title;
    private String description;
    private String imagePath;
    private String rssUrl;
    private boolean subscribed;

    public Podcast() {

    }

//    public static Observable<Podcast> read(DbHelper dbHelper, int id) {
//        return Observable.fromCallable(() -> {
//            Podcast result = null;
//            try (SQLiteDatabase db = dbHelper.getReadableDatabase()) {
//                Cursor cursor = db.rawQuery(SQL_SELECT_BY_ID, new String[] { Integer.toString(id) });
//                try {
//                    cursor.moveToNext();
//                    if (cursor.getCount() == 1) {
//                        result = new Podcast();
//                        // TODO
//                    }
//                }
//                finally {
//                    cursor.close();
//                }
//            }
//            return result;
//        })
//        .subscribeOn(Schedulers.io())
//        .observeOn(AndroidSchedulers.mainThread());
//    }


    public static Future<Cursor> readAllAsync(DbHelper dbHelper) {
        return dbHelper.getExecutorSupplier().get().submit(() -> {
            return dbHelper.getDatabase().rawQuery(SQL_SELECT_ALL, null);
        });
    }

    long write(SQLiteDatabase db) {
        ContentValues vals = new ContentValues(8);
        if (this.getId() != 0)
            vals.put(_ID, this.getId());
        vals.put(CODE, this.getCode().toString());
        vals.put(LEVEL, this.getLevel().ordinal());
        vals.put(TITLE, this.getTitle());
        vals.put(DESCRIPTION, this.getDescription());
        vals.put(IMAGE_PATH, this.getImagePath());
        vals.put(RSS_URL, this.getRssUrl());
        vals.put(SUBSCRIBED, this.isSubscribed());
        this.id = db.insertOrThrow(TABLE_NAME, null, vals);
        return this.id;
    }

    static void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
    }

    public long getId() {
        return id;
    }

    private void setId(long id) {
        this.id = id;
    }

    public UUID getCode() {
        return code;
    }

    public void setCode(UUID code) {
        this.code = code;
    }

    public PodcastLevel getLevel() {
        return level;
    }

    public void setLevel(PodcastLevel level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getRssUrl() {
        return rssUrl;
    }

    public void setRssUrl(String rssUrl) {
        this.rssUrl = rssUrl;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public enum PodcastLevel { BEGINNER, INTERMEDIATE, ADVANCED }
}
