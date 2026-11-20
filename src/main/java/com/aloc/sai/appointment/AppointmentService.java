package com.aloc.sai.appointment;

import com.aloc.sai.appointment.dto.AppointmentConfirmRequest;
import com.aloc.sai.appointment.dto.AppointmentListResponse;
import com.aloc.sai.appointment.dto.AppointmentResponse;
import com.aloc.sai.appointment.dto.AppointmentUpdateRequest;
import com.aloc.sai.chat.*;
import com.aloc.sai.chat.dto.ChatRoomDetailResponse;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
    private static final int MIN_MEMBERS = 2;

    // ChatRoomService를 주입하면 순환 참조가 되므로 채팅 정보는 ChatRoomQueryService로 본다
    private final ChatRoomQueryService chatRoomQueryService;
    private final ChatMessageService chatMessageService;
    private final UserService userService;
    private final FriendService friendService;

    //약속 모집 중
    @Transactional
    public Appointment createRecruiting(ChatRoom room, User host, Integer maxMember, LocalDateTime departAt){
        return appointmentRepository.save(Appointment.recruiting(room,host,maxMember,departAt));
    }

    //약속 확정 PUBLIC을 확정 시키고 DIRECT과 PRIVATE은 채팅 형성 이후에 약속 정함
    @Transactional
    public AppointmentResponse confirm(Long userId, Long chatRoomId, AppointmentConfirmRequest req){
        ChatRoom room=chatRoomQueryService.getRoomForUpdate(chatRoomId);
        checkCanManage(room, userId);
        if(room.isClosed()){
            throw new ApiException(HttpStatus.CONFLICT, "종료된 채팅방입니다.");
        }
        // TODO: 추천 기능이 생기면 루트 확인 후 루트 시간으로 departAt/returnAt 채우기
        if (req.recommendationRouteId() != null) {
            throw badRequest("추천 루트로 확정하는 기능은 아직 지원하지 않습니다.");
        }
        if (req.departAt() == null) {
            throw badRequest("약속 시간을 입력해 주세요.");
        }
        validateTimes(req.departAt(), req.returnAt());

        User me=userService.getUser(userId);
        Optional<Appointment> existing=appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId);
        Appointment appointment;
        if(existing.isPresent()){
            appointment=existing.get();
            if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 확정된 약속입니다. 변경은 약속 변경을 이용해 주세요.");
            }
            appointment.confirm(me, req.departAt(), req.returnAt());
        }
        else{
            appointment=appointmentRepository.save(Appointment.confirmed(room,me,req.departAt(),req.returnAt()));
        }
        chatMessageService.postSystem(room, "약속이 확정되었습니다. (" + req.departAt().format(MESSAGE_TIME) + ")");
        // TODO: 참여자 알림 (알림 타입 추가 필요, 알림 담당과 협의)?
        return toResponse(appointment, userId);
    }

    //약속 변경 및 취소
    @Transactional
    public AppointmentResponse update(Long userId, Long chatRoomId, AppointmentUpdateRequest req){
        ChatRoom room = chatRoomQueryService.getRoomForUpdate(chatRoomId);
        checkCanManage(room, userId);

        Appointment appointment=appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "약속을 찾을 수 없습니다."));
        if(appointment.isCanceled()){
            throw new ApiException(HttpStatus.CONFLICT, "이미 취소된 약속입니다.");
        }

        //취소
        if(req.status()!=null){
            if (!"CANCELED".equals(req.status())) {
                throw badRequest("status는 CANCELED만 보낼 수 있습니다.");
            }
            appointment.cancel();
            chatMessageService.postSystem(room, "약속이 취소되었습니다.");
            // TODO: 참여자 알림
            return toResponse(appointment, userId);
        }

        if (req.departAt() == null && req.returnAt() == null && req.maxMembers() == null) {
            throw badRequest("바꿀 내용이 없습니다.");
        }

        //시간 변경
        if(req.departAt()!=null || req.returnAt()!=null){
            LocalDateTime departAt = req.departAt() != null ? req.departAt() : appointment.getDepartAt();
            LocalDateTime returnAt = req.returnAt() != null ? req.returnAt() : appointment.getReturnAt();
            if (departAt == null) {
                throw badRequest("약속 시간을 먼저 정해 주세요.");
            }
            validateTimes(departAt, returnAt);
            appointment.reschedule(departAt, returnAt);
            chatMessageService.postSystem(room, "약속 시간이 " + departAt.format(MESSAGE_TIME) + "(으)로 바뀌었습니다.");
        }

        //정원변경
        if(req.maxMembers()!=null){
            if(!appointment.isRecruiting()){
                throw badRequest("모집 중인 약속만 정원을 바꿀 수 있습니다.");
            }
            int memberCount = chatRoomQueryService.countMembers(chatRoomId);
            if(req.maxMembers() < MIN_MEMBERS || req.maxMembers() < memberCount){
                throw badRequest("정원은 2명 이상이고 현재 인원(" + memberCount + "명) 이상이어야 합니다.");
            }
            appointment.changeMaxMembers(req.maxMembers());
            chatMessageService.postSystem(room, "모집 정원이 " + req.maxMembers() + "명으로 바뀌었습니다.");
        }
        // TODO: 참여자 알림
        return toResponse(appointment, userId);
    }

    //약속 목록(예정된 약속+ 친구들 모집중인 약속)
    @Transactional(readOnly = true)
    public AppointmentListResponse getAppointments(Long userId){
        LocalDateTime now=LocalDateTime.now();
        List<Long> myRoomIds=chatRoomQueryService.getMyRoomIds(userId);

        return new AppointmentListResponse(
                findUpcoming(userId, myRoomIds, now),
                findRecruitingByFriends(userId, new HashSet<>(myRoomIds))
        );
    }
    //1. 예정된 약속
    private List<AppointmentListResponse.AppointmentItem> findUpcoming(Long userId, List<Long> myRoomIds, LocalDateTime now){
        if(myRoomIds.isEmpty()){
            return List.of();
        }
        return appointmentRepository.findAllByChatRoomIdsAndStatuses(
                        myRoomIds, List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RECRUITING)).stream()
                .filter(a -> a.getDepartAt() == null || a.getDepartAt().isAfter(now))
                .sorted(departAtFirst())
                .map(a -> toItem(a, userId))
                .toList();
    }
    //2. 친구들이 모집 중인 약속
    private List<AppointmentListResponse.AppointmentItem> findRecruitingByFriends(Long userId, Set<Long> myRoomIds){
        List<Long> friendIds=friendService.getFriends(userId,false).friends()
                .stream()
                .map(f->f.friendId())
                .toList();
        if(friendIds.isEmpty()){
            return List.of();
        }

        return appointmentRepository.findAllByHostIds(friendIds,ChatRoomType.PUBLIC,AppointmentStatus.RECRUITING)
                .stream()
                .filter(a -> !a.getChatRoom().isClosed())
                .filter(a->!myRoomIds.contains(a.getChatRoom().getChatRoomId()))
                .filter(a->hasSeatLeft(a))
                .sorted(departAtFirst())
                .map(a -> toItem(a, userId))
                .toList();
    }
    //정원이 없거나 아직 자리가 남았는지
    private boolean hasSeatLeft(Appointment a) {
        return a.getMaxMembers() == null
                || chatRoomQueryService.countMembers(a.getChatRoom().getChatRoomId()) < a.getMaxMembers();
    }

    //모집 중 약속방에 사람 들어올 수 있는지 확인
    @Transactional
    public void validateJoinable(Long chatRoomId, int currentMemberCount){
        Appointment appointment=appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "모집 중인 약속이 없습니다."));
        if(appointment.getStatus()!=AppointmentStatus.RECRUITING){
            throw new ApiException(HttpStatus.CONFLICT, "모집이 끝난 약속입니다.");
        }
        Integer max=appointment.getMaxMembers();
        if(max!=null && currentMemberCount+1>max){
            throw new ApiException(HttpStatus.CONFLICT, "모집 정원이 가득 찼습니다.");
        }
    }

    //약속과 채팅방 매칭하기
    @Transactional(readOnly = true)
    public Optional<Appointment> findByChatRoomId(Long chatRoomId){
        return appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId);
    }

    //채팅방 방장이 바뀌면 약속 주인도 함께 바꾼다(약속이 없는 방은 무시)
    @Transactional
    public void changeHost(Long chatRoomId, User newHost){
        appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId)
                .ifPresent(appointment -> appointment.changeHost(newHost));
    }

    // 탈퇴 트랜잭션 안에서 사용자 행을 지우기 전에 실행된다. DB 정리만 하고 예외를 삼키지 않는다.
    // 채팅방 정리(ChatRoomService, Order 1)가 먼저 방장·약속 주인을 넘긴 뒤에 실행되어야 한다.
    @EventListener
    @Order(2)
    @Transactional
    public void cleanUpOnWithdraw(UserWithdrawEvent event) {
        Long userId = event.userId();
        for (Appointment appointment : appointmentRepository.findAllByUser_UserId(userId)) {
            User roomHost = appointment.getChatRoom().getHost();
            if (roomHost != null && !roomHost.getUserId().equals(userId)) {
                appointment.changeHost(roomHost);
            } else {
                appointmentRepository.delete(appointment);
            }
        }
    }

    //참여자여야 하고 방장이 있는 방(PUBLIC, PRIVATE)은 방장만 가능. DIRECT는 두 사람 모두 가능
    private void checkCanManage(ChatRoom room, Long userId) {
        if (!chatRoomQueryService.isMember(userId, room.getChatRoomId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다.");
        }
        if (room.getType() != ChatRoomType.DIRECT && !room.isHost(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "방장만 약속을 정하거나 바꿀 수 있습니다.");
        }
    }
    private void validateTimes(LocalDateTime departAt, LocalDateTime returnAt) {
        if (!departAt.isAfter(LocalDateTime.now())) {
            throw badRequest("약속 시간은 현재 이후여야 합니다.");
        }
        if (returnAt != null && !returnAt.isAfter(departAt)) {
            throw badRequest("끝나는 시각은 약속 시간 이후여야 합니다.");
        }
    }
    private AppointmentResponse toResponse(Appointment a, Long viewerId) {
        Long roomId = a.getChatRoom().getChatRoomId();
        return AppointmentResponse.of(a, roomName(a.getChatRoom(), viewerId), chatRoomQueryService.countMembers(roomId));
    }

    private AppointmentListResponse.AppointmentItem toItem(Appointment a,Long viewerId){
        ChatRoom room = a.getChatRoom();
        User host = (room.getType() == ChatRoomType.DIRECT) ? null : room.getHost();
        return AppointmentListResponse.AppointmentItem.of(
                a, roomName(room, viewerId), chatRoomQueryService.countMembers(room.getChatRoomId()), host);
    }

    //약속 이름 = 채팅방 이름. DIRECT는 이름이 없어서 상대 닉네임을 쓴다
    private String roomName(ChatRoom room,Long viewerId){
        if(room.getType()==ChatRoomType.DIRECT){
            return chatRoomQueryService.findOpponent(room.getChatRoomId(), viewerId)
                    .map(User::getNickname)
                    .orElse(null);
        }
        return room.getChatRoomName();
    }

    //시간이 정해진 약속을 빠른 순으로 시간 미정은 뒤로
    private Comparator<Appointment> departAtFirst() {
        return Comparator.comparing(Appointment::getDepartAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
