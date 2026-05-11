package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import org.telegram.messenger.MessageObject;
import android.util.SparseArray;
import java.util.ArrayList;

public class AyuHistoryHook {

    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList<MessageObject> messArr) {
        if (messArr == null || messArr.isEmpty()) {
            return new Pair<>(0, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (MessageObject msg : messArr) {
            int id = msg.getId();
            if (id < min) min = id;
            if (id > max) max = id;
        }
        return new Pair<>(min, max);
    }

    @SuppressWarnings("unchecked")
    public static void doHook(int currentAccount, ArrayList<MessageObject> messArr,
                              SparseArray<MessageObject>[] messagesDict,
                              int startId, int endId, long dialogId,
                              int limit, int topicId, boolean isSecretChat) {
        // stub — message history feature not implemented
    }
}
