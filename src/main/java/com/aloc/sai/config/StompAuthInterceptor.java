package com.aloc.sai.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    @Override
    public Message<?>preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor accessor=
                MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
        if(accessor==null){
            return message;
        }

        if(StompCommand.CONNECT.equals(accessor.getCommand())){
            String authHeader=accessor.getFirstNativeHeader("Authorization");
            if(authHeader==null||!authHeader.startsWith("Bearer ")){
                throw new IllegalArgumentException("UNAUTHORIZED");
            }
            String token = authHeader.substring(7);

            //임시
            Long userId=resloveUserId(token);

            accessor.setUser(new UsernamePasswordAuthenticationToken(String.valueOf(userId),null, List.of()));
        }

        return message;
    }

    private Long resloveUserId(String token){
        return Long.valueOf(token);
    }
}
