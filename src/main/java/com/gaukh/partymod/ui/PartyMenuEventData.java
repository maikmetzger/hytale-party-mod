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
    ).add().append(
            new KeyedCodec<>("@PartyName", Codec.STRING),
            (data, value) -> data.partyName = value,
            data -> data.partyName
    ).add().append(
            new KeyedCodec<>("@Password", Codec.STRING),
            (data, value) -> data.password = value,
            data -> data.password
    ).add().build();

    private String action;
    private String target;
    private String partyName;
    private String password;

    @Nonnull
    public String getAction() {
        return action != null ? action : "";
    }

    @Nullable
    public String getTarget() {
        return target;
    }

    @Nullable
    public String getPartyName() {
        return partyName;
    }

    @Nullable
    public String getPassword() {
        return password;
    }
}
