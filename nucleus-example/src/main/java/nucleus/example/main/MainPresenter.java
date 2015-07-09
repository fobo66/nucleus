package nucleus.example.main;

import android.os.Bundle;
import android.support.annotation.NonNull;

import nucleus.example.base.App;
import nucleus.example.base.ServerAPI;
import nucleus.presenter.RxPresenter;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Action2;
import rx.functions.Func0;

public class MainPresenter extends RxPresenter<MainActivity> {

    public static final String NAME_1 = "Chuck Norris";
    public static final String NAME_2 = "Jackie Chan";
    public static final String DEFAULT_NAME = NAME_1;

    private static final int REQUEST_ITEMS = 1;

    private static final String NAME_KEY = "name";

    private String name = DEFAULT_NAME;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null)
            name = savedState.getString(NAME_KEY);

        registerRestartable(REQUEST_ITEMS, new Func0<Subscription>() {
            @Override
            public Subscription call() {
                final String name1 = name;
                return App.getServerAPI()
                    .getItems(name.split("\\s+")[0], name.split("\\s+")[1])
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Deliver<>(DeliveryRule.CACHE,
                        new Action2<MainActivity, ServerAPI.Response>() {
                            @Override
                            public void call(MainActivity activity, ServerAPI.Response response) {
                                activity.onItems(response.items, name1);
                            }
                        },
                        new Action2<MainActivity, Throwable>() {
                            @Override
                            public void call(MainActivity activity, Throwable throwable) {
                                activity.onNetworkError(throwable);
                            }
                        }));
            }
        });

        registerRestartable(REQUEST_ITEMS, new Func0<Subscription>() {
            @Override
            public Subscription call() {
                final String name1 = name;
                return App.getServerAPI()
                    .getItems(name.split("\\s+")[0], name.split("\\s+")[1])
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(MainPresenter.this.<ServerAPI.Response>deliver(DeliveryRule.CACHE))
                    .subscribe(new Action1<Delivery<ServerAPI.Response>>() {
                        @Override
                        public void call(Delivery<ServerAPI.Response> delivery) {
                            delivery.split(
                                new Action2<MainActivity, ServerAPI.Response>() {
                                    @Override
                                    public void call(MainActivity activity, ServerAPI.Response response) {
                                        activity.onItems(response.items, name1);
                                    }
                                },
                                new Action2<MainActivity, Throwable>() {
                                    @Override
                                    public void call(MainActivity activity, Throwable throwable) {
                                        activity.onNetworkError(throwable);
                                    }
                                });
                        }
                    });
            }
        });

        registerRestartable(REQUEST_ITEMS, new Func0<Subscription>() {
            @Override
            public Subscription call() {
                final String name1 = name;
                return App.getServerAPI()
                    .getItems(name.split("\\s+")[0], name.split("\\s+")[1])
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(MainPresenter.this.<ServerAPI.Response>deliver(DeliveryRule.CACHE))
                    .subscribe(new DeliverDelivery<>(
                        new Action2<MainActivity, ServerAPI.Response>() {
                            @Override
                            public void call(MainActivity activity, ServerAPI.Response response) {
                                activity.onItems(response.items, name1);
                            }
                        },
                        new Action2<MainActivity, Throwable>() {
                            @Override
                            public void call(MainActivity activity, Throwable throwable) {
                                activity.onNetworkError(throwable);
                            }
                        }));
            }
        });

        registerRestartable(REQUEST_ITEMS, new Func0<Subscription>() {
            @Override
            public Subscription call() {
                final String name1 = name;
                return App.getServerAPI()
                    .getItems(name.split("\\s+")[0], name.split("\\s+")[1])
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(new DeliveryTransformer<ServerAPI.Response>(DeliveryRule.CACHE))
                    .subscribe(new DeliverDelivery<>(
                        new Action2<MainActivity, ServerAPI.Response>() {
                            @Override
                            public void call(MainActivity activity, ServerAPI.Response response) {
                                activity.onItems(response.items, name1);
                            }
                        },
                        new Action2<MainActivity, Throwable>() {
                            @Override
                            public void call(MainActivity activity, Throwable throwable) {
                                activity.onNetworkError(throwable);
                            }
                        }));
            }
        });
    }

    @Override
    public void onSave(@NonNull Bundle state) {
        super.onSave(state);
        state.putString(NAME_KEY, name);
    }

    public void request(String name) {
        this.name = name;
        subscribeRestartable(REQUEST_ITEMS);
    }
}