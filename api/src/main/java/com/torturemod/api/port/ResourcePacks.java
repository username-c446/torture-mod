package com.torturemod.api.port;

import com.torturemod.api.model.TargetId;

/** Отправка ресурспака игроку: клиент скачает его по ссылке. */
public interface ResourcePacks {
    void offer(TargetId player, String url, String sha1, boolean required, String prompt);
}
