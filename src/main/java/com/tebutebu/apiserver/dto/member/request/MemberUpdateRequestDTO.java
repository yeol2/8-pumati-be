package com.tebutebu.apiserver.dto.member.request;

import com.tebutebu.apiserver.domain.enums.Course;
import com.tebutebu.apiserver.domain.enums.MemberRole;
import com.tebutebu.apiserver.global.constant.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberUpdateRequestDTO {

    @NotBlank(message = ValidationMessages.MEMBER_NAME_REQUIRED)
    @Size(max = 10, message = ValidationMessages.MEMBER_NAME_MAX_LENGTH_EXCEEDED)
    private String name;

    @NotBlank(message = ValidationMessages.MEMBER_NICKNAME_REQUIRED)
    @Size(max = 50, message = ValidationMessages.AI_COMMENT_NICKNAME_MAX_LENGTH_EXCEEDED)
    @Pattern(regexp = "^\\S+$", message = ValidationMessages.MEMBER_NICKNAME_VIOLATED)
    private String nickname;

    private String profileImageUrl;

    @Positive(message = ValidationMessages.TERM_MUST_BE_POSITIVE)
    private Integer term;

    @Positive(message = ValidationMessages.TEAM_NUMBER_MUST_BE_POSITIVE)
    private Integer teamNumber;

    private Course course;

    private MemberRole role;

}
