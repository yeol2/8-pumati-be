package com.tebutebu.apiserver.service.member;

import com.github.javafaker.Faker;
import com.tebutebu.apiserver.domain.Member;
import com.tebutebu.apiserver.domain.enums.MemberRole;
import com.tebutebu.apiserver.domain.enums.MemberState;
import com.tebutebu.apiserver.domain.Team;
import com.tebutebu.apiserver.dto.member.request.AiMemberSignupRequestDTO;
import com.tebutebu.apiserver.dto.member.request.MemberOAuthSignupRequestDTO;
import com.tebutebu.apiserver.dto.member.request.MemberUpdateRequestDTO;
import com.tebutebu.apiserver.dto.member.response.MemberResponseDTO;
import com.tebutebu.apiserver.dto.member.response.MemberSignupResponseDTO;
import com.tebutebu.apiserver.dto.oauth.request.OAuthCreateRequestDTO;
import com.tebutebu.apiserver.dto.team.response.TeamResponseDTO;
import com.tebutebu.apiserver.global.errorcode.BusinessErrorCode;
import com.tebutebu.apiserver.repository.MemberRepository;
import com.tebutebu.apiserver.security.dto.CustomOAuth2User;
import com.tebutebu.apiserver.service.oauth.OAuthService;
import com.tebutebu.apiserver.service.token.RefreshTokenService;
import com.tebutebu.apiserver.service.team.TeamService;
import com.tebutebu.apiserver.global.exception.BusinessException;
import com.tebutebu.apiserver.util.CookieUtil;
import com.tebutebu.apiserver.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    @Value("${spring.jwt.refresh.cookie.name}")
    private String refreshCookieName;

    @Value("${spring.jwt.refresh.cookie.max-age}")
    private int refreshCookieMaxAge;

    @Value("${default.profile.image.pu.url}")
    private String defaultProfileImagePuUrl;

    @Value("${default.profile.image.mati.url}")
    private String defaultProfileImageMatiUrl;

    private final MemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder;

    private final TeamService teamService;

    private final OAuthService oauthService;

    private final RefreshTokenService refreshTokenService;

    private final CookieUtil cookieUtil;

    @Override
    public MemberResponseDTO get(Long memberId) {
        Member member = memberRepository.findByIdWithTeam(memberId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MEMBER_NOT_FOUND));
        return entityToDTO(member);
    }

    @Override
    public MemberResponseDTO get(String authorizationHeader) {
        Long memberId = extractMemberIdFromHeader(authorizationHeader);
        Member member = memberRepository.findByIdWithTeam(memberId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MEMBER_NOT_FOUND));
        return entityToDTO(member);
    }

    @Override
    public List<MemberResponseDTO> getMembersByTeamId(Long teamId) {
        List<Member> members = memberRepository.findAllByTeamIdWithTeam(teamId);
        if (members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .map(this::entityToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public MemberSignupResponseDTO registerOAuthUser(MemberOAuthSignupRequestDTO dto,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {

        var auth = JWTUtil.parseSignupToken(dto.getSignupToken());
        String provider = auth.provider();
        String providerId = auth.providerId();
        String email = auth.email();

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(BusinessErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Member member = memberRepository.save(dtoToEntity(dto, email));

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(member);
        Map<String, Object> attributes = customOAuth2User.getAttributes();

        Long memberId = member.getId();

        String accessToken = JWTUtil.generateAccessToken(attributes);
        String refreshToken= JWTUtil.generateRefreshToken(memberId);
        refreshTokenService.persistRefreshToken(memberId, refreshToken);

        cookieUtil.addRefreshTokenCookie(
                response,
                refreshCookieName,
                refreshToken,
                refreshCookieMaxAge,
                request.isSecure()
        );

        OAuthCreateRequestDTO oauthDto = OAuthCreateRequestDTO.builder()
                .memberId(member.getId())
                .provider(provider)
                .providerId(providerId)
                .build();
        oauthService.register(oauthDto);

        return MemberSignupResponseDTO.builder()
                .id(member.getId())
                .teamId(member.getTeam() != null ? member.getTeam().getId() : null)
                .email(member.getEmail())
                .name(member.getName())
                .nickname(member.getNickname())
                .role(member.getRole().name())
                .state(member.getState().name())
                .accessToken(accessToken)
                .build();
    }

    @Override
    public Long registerAiMember(AiMemberSignupRequestDTO dto) {
        String email = generateUniqueAiEmail();

        Member member = Member.builder()
                .email(email)
                .password(passwordEncoder.encode("!A1ai-comment"))
                .name(dto.getName())
                .nickname(dto.getNickname())
                .profileImageUrl(getRandomDefaultProfileImageUrl())
                .isSocial(false)
                .role(MemberRole.USER)
                .state(MemberState.ACTIVE)
                .build();
        return memberRepository.save(member).getId();
    }

    @Override
    public void modify(String authorizationHeader, MemberUpdateRequestDTO dto) {
        Long memberId = extractMemberIdFromHeader(authorizationHeader);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MEMBER_NOT_FOUND));

        if (dto.getTerm() != null && dto.getTeamNumber() != null) {
            TeamResponseDTO teamResponseDTO = teamService.getByTermAndNumber(dto.getTerm(), dto.getTeamNumber());
            member.changeTeam(teamResponseDTO == null ? null : Team.builder().id(teamResponseDTO.getId()).build());
        }

        if (dto.getProfileImageUrl() != null && !dto.getProfileImageUrl().isEmpty()) {
            member.changeProfileImageUrl(dto.getProfileImageUrl());
        }

        if (dto.getName() != null && !dto.getName().isEmpty()) {
            member.changeName(dto.getName());
        }

        if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
            member.changeNickname(dto.getNickname());
        }

        if (dto.getCourse() != null) {
            member.changeCourse(dto.getCourse());
        }

        if (dto.getRole() != null) {
            member.changeRole(dto.getRole());
        }

        memberRepository.save(member);
    }

    @Override
    public void toggleEmailConsent(String authorizationHeader) {
        Long memberId = extractMemberIdFromHeader(authorizationHeader);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MEMBER_NOT_FOUND));

        if (member.hasEmailConsent()) {
            member.declineToReceiveEmail();
        } else {
            member.agreeToReceiveEmail();
        }

        memberRepository.save(member);
    }

    @Override
    public void delete(String authorizationHeader, HttpServletRequest request, HttpServletResponse response) {
        Long memberId = extractMemberIdFromHeader(authorizationHeader);

        refreshTokenService.deleteByMemberId(memberId);
        oauthService.deleteByMemberId(memberId);

        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(BusinessErrorCode.MEMBER_NOT_FOUND);
        }

        refreshTokenService.deleteByMemberId(memberId);
        cookieUtil.deleteRefreshTokenCookie(
                response,
                refreshCookieName,
                request.isSecure()
        );

        memberRepository.deleteById(memberId);
    }

    public Long extractMemberIdFromHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("invalidToken");
        }
        String token = authorizationHeader.substring(7);
        Map<String,Object> claims = JWTUtil.validateToken(token);
        Number idClaim = (Number) claims.get("id");
        if (idClaim == null) {
            throw new IllegalArgumentException("invalidToken");
        }
        return idClaim.longValue();
    }

    @Override
    public Member dtoToEntity(MemberOAuthSignupRequestDTO dto, String email) {
        TeamResponseDTO teamResponseDTO = teamService.getByTermAndNumber(dto.getTerm(), dto.getTeamNumber());
        return Member.builder()
                .team(teamResponseDTO == null ? null : Team.builder().id(teamResponseDTO.getId()).build())
                .email(email)
                .password(passwordEncoder.encode(new Faker().internet().password()))
                .name(dto.getName())
                .nickname(dto.getNickname())
                .course(dto.getCourse())
                .profileImageUrl(dto.getProfileImageUrl() == null ? getRandomDefaultProfileImageUrl() : dto.getProfileImageUrl())
                .role(dto.getRole())
                .build();
    }

    private String generateUniqueAiEmail() {
        return "ai_" + UUID.randomUUID() + "@tebutebu.ai";
    }

    private String getRandomDefaultProfileImageUrl() {
        return ThreadLocalRandom.current().nextBoolean()
                ? defaultProfileImagePuUrl
                : defaultProfileImageMatiUrl;
    }

}
