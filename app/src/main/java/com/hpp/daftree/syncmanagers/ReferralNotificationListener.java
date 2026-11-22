package com.hpp.daftree.syncmanagers;

public interface ReferralNotificationListener {
    void onReferralRewardReceived(String userId, long newPoints,String notiMessegeTitel,String notiMessege);
}
