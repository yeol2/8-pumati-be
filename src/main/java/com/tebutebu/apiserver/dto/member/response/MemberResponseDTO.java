package com.tebutebu.apiserver.dto.member.response;

import com.tebutebu.apiserver.domain.enums.Course;
import com.tebutebu.apiserver.domain.enums.MemberRole;
import com.tebutebu.apiserver.domain.enums.MemberState;
import lombok.Getter;
import lombok.Builder;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MemberResponseDTO {

    private Long id;

    private Long teamId;

    private Integer term;

    private Integer teamNumber;

    private String email;

    private String password;

    private boolean isSocial;

    private String name;

    private String nickname;

    private Course course;

    private String profileImageUrl;

    private MemberRole role;

    private MemberState state;

    private Boolean hasEmailConsent;

    private LocalDateTime createdAt, modifiedAt;

}
