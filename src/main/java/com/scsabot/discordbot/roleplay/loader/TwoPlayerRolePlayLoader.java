package com.scsabot.discordbot.roleplay.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TwoPlayerRolePlayLoader extends RolePlayDataLoader {

    public TwoPlayerRolePlayLoader(ObjectMapper objectMapper) {
        super(objectMapper, 2, "roleplay_2P_");
    }
}