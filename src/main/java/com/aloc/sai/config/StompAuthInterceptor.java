package com.aloc.sai.config;

import com.aloc.sai.chat.ChatRoomService;
import com.aloc.sai.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern ROOM_SUB = Pattern.compile("^/sub/chat-rooms/(\\d+)$");
    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomService chatRoomService;

    @Override
    public Message<?>preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;

        switch (acc.getCommand()){
            case CONNECT -> {
                String h=acc.getFirstNativeHeader("Authorization");
                if(h==null||!h.startsWith("Bearer ")){
                    throw new MessageDeliveryException("UNAUTHORIZED");
                }
                String token=h.substring(7);
                if(!jwtTokenProvider.validateToken(token) || !"access".equals(jwtTokenProvider.getTokenType(token))){
                    throw new MessageDeliveryException("UNAUTHORIZED");
                }
                acc.setUser(jwtTokenProvider.getAuthentication(token));
            }

            //이 사람이 이 방 메시지를 받아도 괜찮은가?(SUBSCRIBE  destination: /sub/chat-rooms/12 12번 방의 메시지 보내주어라)
            case SUBSCRIBE -> {
                //채팅방 구독 주소가 맞는지 확인
                Matcher m=ROOM_SUB.matcher(String.valueOf(acc.getDestination()));
                if(m.matches()){
                    Long userId=Long.valueOf(acc.getUser().getName());
                    //m.group(1)은 12로 방 번호이다
                    chatRoomService.checkMember(userId,Long.valueOf(m.group(1)));
                }
            }
            default -> {}
        }
        return message;
    }
}
