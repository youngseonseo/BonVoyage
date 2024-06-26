package com.ssafy.BonVoyage.auth.domain;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Data
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    private Authority authority;

    @Column
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private Grade grade;

    @Column
    private String birth;

    @Column
    private String phone;

    public void updateName(String name){
        this.username = name;
    }
    public void updateImageUrl(String imageUrl){
        this.imageUrl = imageUrl;
    }


}
