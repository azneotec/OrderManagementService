package com.azneotech.ordermanagementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Where the order is delivered; copied onto the order so later profile changes don't rewrite history. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class ShippingAddress {

    @Column(name = "ship_full_name", nullable = false, length = 100)
    private String fullName;
    @Column(name = "ship_phone_number", nullable = false, length = 20)
    private String phoneNumber;
    @Column(name = "ship_address_line1", nullable = false, length = 200)
    private String addressLine1;
    @Column(name = "ship_address_line2", length = 200)
    private String addressLine2;
    @Column(name = "ship_city", nullable = false, length = 100)
    private String city;
    @Column(name = "ship_state", nullable = false, length = 100)
    private String state;
    @Column(name = "ship_postal_code", nullable = false, length = 20)
    private String postalCode;
    @Column(name = "ship_country", nullable = false, length = 100)
    private String country;

}
