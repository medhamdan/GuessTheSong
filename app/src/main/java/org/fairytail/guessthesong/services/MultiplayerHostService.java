package org.fairytail.guessthesong.services;

import android.net.wifi.WifiManager;
import android.support.v4.util.Pair;

import com.f2prateek.rx.receivers.wifi.RxWifiManager;
import com.github.davidmoten.rx.Bytes;
import com.peak.salut.Callbacks.SalutDeviceCallback;
import com.peak.salut.SalutDevice;

import org.fairytail.guessthesong.helpers.MpGameConverter;
import org.fairytail.guessthesong.lib.ReactiveList;
import org.fairytail.guessthesong.lib.ReactiveMap;
import org.fairytail.guessthesong.model.game.Game;
import org.fairytail.guessthesong.model.game.MpGame;
import org.fairytail.guessthesong.networking.entities.SocketMessage;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

import in.workarounds.bundler.annotations.RequireBundler;
import lombok.val;
import ru.noties.debug.Debug;
import rx.Observable;
import rx.Subscription;

@RequireBundler
public class MultiplayerHostService extends MultiplayerService {

    public class NetworkServiceStartException extends Exception {
        public NetworkServiceStartException() {
            super("Network service can't be started");
        }

        public NetworkServiceStartException(String detailMessage) {
            super(detailMessage);
        }

        public NetworkServiceStartException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public NetworkServiceStartException(Throwable throwable) {
            super(throwable);
        }
    }

    private ReactiveMap<String, SalutDevice> players = new ReactiveMap<>();
    private ReactiveList<String> preparedPlayers = new ReactiveList<>();

    private SalutDeviceCallback deviceRegisteredReaction = device -> {
        Debug.d(device.readableName + " has connected!");
        players.put(device.txtRecord.get("id"), device);
    };

    @Override
    protected Subscription[] subscribeListeners() {
        val subs = new Subscription[4];

        subs[0] = players.onItemAdded()
                         .subscribe(event -> {
                             Debug.d(event.getValue().readableName + " shared info about him!");
                             network.sendToDevice(event.getValue(),
                                                  msgFactory.newPrepareRequest(currentGame),
                                                  () -> Debug.e("Can't prepare client"));
                         });

        subs[1] = preparedPlayers.onItemAdded()
                                 .map(evt -> players.get(evt.getItem()))
                                 .subscribe(player -> Debug.d(player.readableName + " has prepared!"));

        subs[2] = responses.filter(msg -> msg.message == SocketMessage.Message.PREPARE)
                           .filter(msg -> msg.status == SocketMessage.Status.OK)
                           .subscribe(msg -> preparedPlayers.add(msg.userId));

        subs[3] = requests.filter(msg -> msg.message == SocketMessage.Message.SONG)
                          .concatMap(msg -> {
                              try {
                                  return Bytes.from(new BufferedInputStream(new FileInputStream(msg.body)))
                                              .map(bytes -> Pair.<byte[], SocketMessage>create(bytes, msg));
                              } catch (FileNotFoundException e) {
                                  return Observable.error(e);
                              }
                          })
                          .subscribe(pair -> {
                              network.sendToDevice(players.get(pair.second.userId),
                                                   new String(pair.first),
                                                   () -> Debug.e("Can't send a song to the client"));
                          });

        return subs;
    }

    private Observable<Void> startNetworkServiceIfNotAlreadyStarted() {
        return Observable.<Void>create(subscriber -> {
            if (!network.isRunningAsHost) {
                network.startNetworkService(deviceRegisteredReaction,
                                            () -> {
                                                subscriber.onNext(null);
                                                subscriber.onCompleted();
                                            },
                                            () -> subscriber.onError(new NetworkServiceStartException()));
            } else {
                subscriber.onNext(null);
                subscriber.onCompleted();
            }
        });
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

    public Observable<MpGame> prepareNewGame(Game game) {
        return new MpGameConverter(this).convertToMpGame(game)
                                        .concatMap(mpGame -> enableWiFiIfNecessary().map(arg -> mpGame))
                                        .concatMap(mpGame -> startNetworkServiceIfNotAlreadyStarted().map(arg -> mpGame))
                                        .doOnNext(mpGame1 -> currentGame = mpGame1);
    }
}
