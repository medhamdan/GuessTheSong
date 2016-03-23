package org.fairytail.guessthesong.services;

import android.net.wifi.WifiManager;

import com.f2prateek.rx.receivers.wifi.RxWifiManager;
import com.github.davidmoten.rx.Checked;
import com.google.common.io.Files;
import com.peak.salut.Salut;

import org.fairytail.guessthesong.helpers.JSON;
import org.fairytail.guessthesong.model.Song;
import org.fairytail.guessthesong.model.game.MpGame;
import org.fairytail.guessthesong.model.game.Quiz;
import org.fairytail.guessthesong.networking.entities.SocketMessage;

import java.io.File;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import in.workarounds.bundler.annotations.RequireBundler;
import lombok.val;
import ru.noties.debug.Debug;
import rx.Observable;
import rx.Subscription;

@RequireBundler
public class MultiplayerClientService extends MultiplayerService {

    public Salut getNetwork() {
        return network;
    }

    @Override
    protected Subscription[] subscribeListeners() {
        val subs = new Subscription[1];

        subs[0] = requests.filter(msg -> msg.message == SocketMessage.Message.PREPARE)
                          .map(msg -> JSON.parseSilently(msg.body, MpGame.class))
                          .concatMap(this::prepareNewGame)
                          .subscribe(mpGame -> network.sendToHost(
                                  msgFactory.newPrepareCompletedResponse(),
                                  () -> Debug.e("Can't send a prepare completion response to the host.")));

        return subs;
    }

    private Observable<Void> enableWiFiIfNecessary() {
        return Observable.defer(() -> {
            if (!wifiManager.isWifiEnabled()) {
                return RxWifiManager.wifiStateChanges(getApplicationContext())
                                    .filter(state -> state == WifiManager.WIFI_STATE_ENABLED)
                                    .take(1)
                                    .map(s -> (Void) null)
                                    .delay(1, TimeUnit.SECONDS)
                                    .timeout(5, TimeUnit.SECONDS)
                                    .doOnSubscribe(() -> wifiManager.setWifiEnabled(true));
            }
            return Observable.just(null);
        });
    }

    private Observable<MpGame> prepareNewGame(MpGame game) {
        Observable<MpGame> observable = Observable.just(game);
        for (Quiz q : game.getGame().getQuizzes()) {
            val song = q.getCorrectSong();
            observable = observable.concatMap(mpGame -> prepareSong(song).map(s -> mpGame));
        }
        return observable;
    }

    private Observable<Song> prepareSong(Song s) {
        return responses.filter(msg -> msg.message == SocketMessage.Message.SONG)
                        .take(1)
                        .doOnNext(Checked.a1(msg -> Files.write(msg.body, new File(s.getSource()), Charset.defaultCharset())))
                        .doOnSubscribe(() -> network.sendToHost(msgFactory.newSongRequest(s.getSource()),
                                                                () -> Debug.e("Can't request a song from the host!")))
                        .map(msg -> s);
    }

}
