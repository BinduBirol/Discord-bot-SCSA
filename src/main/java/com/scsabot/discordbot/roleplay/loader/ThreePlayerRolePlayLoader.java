package com.scsabot.discordbot.roleplay.loader;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ThreePlayerRolePlayLoader extends RolePlayDataLoader {

    public ThreePlayerRolePlayLoader(ObjectMapper objectMapper) {
        super(objectMapper, 3, "roleplay_3P_");
    }
}