package ru.vinyarsky.englishmedia.media;

import android.util.Xml;

import java.io.IOException;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Future;

import com.google.firebase.crash.FirebaseCrash;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.vinyarsky.englishmedia.db.DbHelper;
import ru.vinyarsky.englishmedia.db.Episode;
import ru.vinyarsky.englishmedia.db.Podcast;

public final class RssFetcher {

    private DbHelper dbHelper;
    private OkHttpClient httpClient;

    public RssFetcher(DbHelper dbHelper, OkHttpClient httpClient) {
        this.dbHelper = dbHelper;
        this.httpClient = httpClient;
    }

    /**
     * Fetches episodes from rss-feed and writes them to db
     * @return Total number of fetched episodes
     */
    public Future<Integer> fetchEpisodesAsync(List<UUID> podcastCodes) {
        return Observable.fromIterable(podcastCodes)
                .subscribeOn(Schedulers.io())
                .map((podcastCode) -> {
                    Podcast podcast = Podcast.read(this.dbHelper, podcastCode);
                    if (podcast != null) {
                        Request request = new Request.Builder()
                                .url(podcast.getRssUrl())
                                .build();
                        try (Response response = this.httpClient.newCall(request).execute()) {
                            if (response.isSuccessful()) {
                                XmlPullParser parser = Xml.newPullParser();
                                parser.setInput(response.body().charStream());
                                parser.next();
                                int count = processRss(parser, podcastCode);
                                parser.setInput(null); // Release internal structures
                                return count;
                            }
                        }
                    }
                    else {
                        FirebaseCrash.report(new Exception(String.format("podcast == null (podcastCode: %s)", podcastCode != null ? podcastCode.toString() : "null")));
                    }
                    return 0;
                })
                .reduce(0, (acc, value) -> acc + value)
                .toFuture();
    }

    /**
     * Parses RSS 2.0 xml
     * https://cyber.harvard.edu/rss/rss.html
     * @implNote Must be thread-safe
     */
    private int processRss(XmlPullParser parser, UUID podcastCode) {
        int count = 0;
        try {
            parser.require(XmlPullParser.START_TAG, null, "rss");
            if (!"2.0".equals(parser.getAttributeValue(null, "version"))) {
                FirebaseCrash.report(new Exception(String.format("rss version must be 2.0 (podcastCode: %s)", podcastCode != null ? podcastCode.toString() : "null")));
                return count;
            }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;
                if (!"item".equals(parser.getName()))
                    continue;
                Episode episode = parseRssItem(parser);
                if (episode.getEpisodeGuid() != null && episode.getContentUrl() != null) {
                    boolean alreadyInDb = Episode.existsByPodcastCodeAndGuid(this.dbHelper, podcastCode, episode.getEpisodeGuid());
                    if (!alreadyInDb) {
                        episode.setPodcastCode(podcastCode);
                        episode.write(this.dbHelper);
                        count++;
                    }
                    else {
                        // Episodes are sorted in files by pubDate. As soon as we meet an episode
                        // that is already saved, we can stop processing file.
                        break;
                    }
                }
            }
        } catch (XmlPullParserException | IOException e) {
            FirebaseCrash.report(e);
        }
        return count;
    }

    /**
     * @implNote Must be thread-safe
     */
    private Episode parseRssItem(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "item");
        Episode episode = new Episode();
        episode.setCurrentPosition(0);
        String currentTag = "";
        SimpleDateFormat dateFormatYYYY = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.US);
        SimpleDateFormat dateFormatYY = new SimpleDateFormat("E, dd MMM yy HH:mm:ss z", Locale.US);
        while (!(parser.next() == XmlPullParser.END_TAG && "item".equals(parser.getName()))) {
            int eventType = parser.getEventType();
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
                if ("enclosure".equals(currentTag)) {
                    episode.setContentUrl(parser.getAttributeValue(null, "url"));
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                currentTag = "";
            }
            else if (eventType == XmlPullParser.TEXT && !"".equals(currentTag)) {
                String value = parser.getText();
                switch (currentTag) {
                    case "title":
                        episode.setTitle(value);
                        break;
                    case "description":
                        episode.setDescription(value);
                        break;
                    case "guid":
                        episode.setEpisodeGuid(value);
                        break;
                    case "pubDate":
                        try {
                            episode.setPubDate(dateFormatYYYY.parse(value));
                        }
                        catch (ParseException e1) {
                            try {
                                episode.setPubDate(dateFormatYY.parse(value));
                            }
                            catch (ParseException e2) {
                                episode.setPubDate(new Date());
                            }
                        }
                        break;
                    case "link":
                        episode.setPageUrl(value);
                        break;
                    case "duration": // itunes:duration
                        int duration = 0;
                        // 1:22:10 or 4930
                        String[] values = value.split(":");
                        int k = 1;
                        for (int i = values.length - 1; i >= 0; i--) {
                            duration += Integer.valueOf(values[i]) * k;
                            k = k * 60;
                        }
                        episode.setDuration(duration);
                        break;
                }
            }
        }
        return episode;
    }

    public /* synchronized */ void close() {
        // Do nothing
    }
}
