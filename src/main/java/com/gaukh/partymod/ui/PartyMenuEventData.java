package com.gaukh.partymod.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PartyMenuEventData {

    public static final BuilderCodec<PartyMenuEventData> CODEC = BuilderCodec.builder(
            PartyMenuEventData.class,
            PartyMenuEventData::new
    ).append(
            new KeyedCodec<>("Action", Codec.STRING),
            (data, value) -> data.action = value,
            data -> data.action
    ).add().append(
            new KeyedCodec<>("Target", Codec.STRING),
            (data, value) -> data.target = value,
            data -> data.target
    ).add().build();

    private String action;
    private String target;

    @Nonnull
    public String getAction() {
        return action != null ? action : "";
    }

    @Nullable
    public String getTarget() {
        return target;
    }
}
