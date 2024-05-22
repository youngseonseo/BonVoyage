package com.ssafy.BonVoyage.group.service;

import com.ssafy.BonVoyage.auth.config.security.token.CurrentUser;
import com.ssafy.BonVoyage.auth.config.security.token.UserPrincipal;
import com.ssafy.BonVoyage.auth.domain.Member;
import com.ssafy.BonVoyage.auth.repository.MemberRepository;
import com.ssafy.BonVoyage.file.service.S3Service;
import com.ssafy.BonVoyage.group.domain.GroupWithMember;
import com.ssafy.BonVoyage.group.domain.TravelGroup;
import com.ssafy.BonVoyage.group.dto.request.GroupCreateRequest;
import com.ssafy.BonVoyage.group.dto.request.GroupInviteRequest;
import com.ssafy.BonVoyage.group.dto.response.GroupInviteResponse;
import com.ssafy.BonVoyage.group.dto.response.GroupMemberResponse;
import com.ssafy.BonVoyage.group.exception.GroupException;
import com.ssafy.BonVoyage.group.repository.GroupWithMemberRepository;
import com.ssafy.BonVoyage.group.repository.TravelGroupRepository;
import com.ssafy.BonVoyage.util.RandomUtil;
import com.ssafy.BonVoyage.util.RedisUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ssafy.BonVoyage.group.exception.GroupExceptionType.*;


@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final TravelGroupRepository travelGroupRepository;
    private final GroupWithMemberRepository groupWithMemberRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;
    private RedisUtil redisUtil;
    @Value("${cloud.address}")
    private String CLOUD_FRONT_DOMAIN_NAME;
    private static final String INVITE_LINK_PREFIX = "groupdId=%d";

    public void createGroup(final GroupCreateRequest request, final MultipartFile file, @CurrentUser UserPrincipal userPrincipal) throws IOException {
        Long ownerId = userPrincipal.getId();
        Optional<Member> member = memberRepository.findById(ownerId);
        final String imageUrl = s3Service.upload(file);

        try {
            final TravelGroup team = TravelGroup.builder()
                    .groupName(request.name())
                    .description(request.description())
                    .groupProfileImage(CLOUD_FRONT_DOMAIN_NAME+ imageUrl)
                    .owner(ownerId)
                    .build();
            TravelGroup travelGroup = travelGroupRepository.save(team);
            if(member.isPresent()) {
                groupWithMemberRepository.save(new GroupWithMember(member.get(), travelGroup.getId()));
            }


        } catch (Exception e) {
            s3Service.delete(imageUrl);
        }
    }
    public void updateGroup(final Long groupId, final GroupCreateRequest request) throws GroupException {
        final TravelGroup travelGroup = validateExistTeam(groupId);
        travelGroup.update(request.name(), request.description());
    }

    public void updateGroupImage(final Long groupId, final MultipartFile file) throws GroupException, IOException {
        final TravelGroup group = validateExistTeam(groupId);

        if (group.hasImage()) {
            final String beforeImageUrl = group.getGroupProfileImage();
            s3Service.delete(beforeImageUrl);
        }
        final String newImageUrl = s3Service.upload(file);
        group.updateImageUrl(newImageUrl);
    }

    public void deleteGroup(final Long groupId) throws GroupException {
        final TravelGroup team = validateExistTeam(groupId);
        travelGroupRepository.delete(team);
        if (team.hasImage()) {
            s3Service.delete(team.getGroupProfileImage());
        }
    }


    private TravelGroup validateExistTeam(final Long groupId) throws GroupException {
        return travelGroupRepository.findById(groupId)
                .orElseThrow(() -> new GroupException(NOT_FOUND_TEAM));
    }

    public GroupInviteResponse generateInviteCode(final Long groupId) throws GroupException {
        validateExistTeam(groupId);

        final Optional<String> link = redisUtil.getData(INVITE_LINK_PREFIX.formatted(groupId), String.class);
        if (link.isEmpty()) {
            final String randomCode = RandomUtil.generateRandomCode('0', 'z', 10);
            redisUtil.setDataExpire(INVITE_LINK_PREFIX.formatted(groupId), randomCode, RedisUtil.toTomorrow());
            return new GroupInviteResponse(randomCode);
        }
        return new GroupInviteResponse(link.get());
    }

    public void joinTeam(final Long teamId, final GroupInviteRequest request) throws GroupException {
        validateExistTeam(teamId);
        Optional<Member> member = memberRepository.findByEmail(request.email());
        if(member.isPresent()) {
            groupWithMemberRepository.save(new GroupWithMember(member.get(), teamId));
        }else {
            throw new GroupException(EXPIRED_LINK);
        }
    }

    private void validateMatchLink(final String link, final String userLink) throws GroupException {
        if (!link.equals(userLink)) {
            throw new GroupException(NOT_MATCH_LINK);
        }
    }

    public List<GroupMemberResponse> findGroupMembers(Long groupId) {
        List<Member> members = groupWithMemberRepository.findMemberByGroupId(groupId);
        log.info(String.valueOf(members.size()));
        return members.stream()
                .map(member -> GroupMemberResponse.toDto(member))
                .collect(Collectors.toUnmodifiableList());
    }

}
