package com.azneotech.ordermanagementservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The message NotificationService turns into an email; same shape UserAuthService publishes on its topics. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailDto {

    private String to;
    private String from;
    private String subject;
    private String body;

}
