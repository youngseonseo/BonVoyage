package com.ssafy.BonVoyage.auth.service;



import com.ssafy.BonVoyage.auth.advice.error.ExpiredTokenException;
import com.ssafy.BonVoyage.auth.advice.payload.ErrorCode;
import com.ssafy.BonVoyage.auth.config.OAuth2Config;
import com.ssafy.BonVoyage.auth.config.security.token.UserPrincipal;
import com.ssafy.BonVoyage.auth.domain.TokenMapping;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;


@Slf4j
@Service
public class CustomTokenProviderService {

    @Autowired
    private OAuth2Config oAuth2Config;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    public TokenMapping refreshToken(Authentication authentication, String refreshToken) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Date now = new Date();

        Date accessTokenExpiresIn = new Date(now.getTime() + oAuth2Config.getAuth().getAccessTokenExpirationMsec());

        String secretKey = oAuth2Config.getAuth().getTokenSecret();
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        String accessToken = Jwts.builder()
                .setSubject(Long.toString(userPrincipal.getId()))
                .setIssuedAt(new Date())
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        return TokenMapping.builder()
                .userEmail(userPrincipal.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public TokenMapping createToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Date now = new Date();

        Date accessTokenExpiresIn = new Date(now.getTime() + oAuth2Config.getAuth().getAccessTokenExpirationMsec());
        Date refreshTokenExpiresIn = new Date(now.getTime() + oAuth2Config.getAuth().getRefreshTokenExpirationMsec());

        String secretKey = oAuth2Config.getAuth().getTokenSecret();
        log.info("secretKey = " + secretKey);
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        log.info("keyBytes = " + keyBytes);
        Key key = Keys.hmacShaKeyFor(keyBytes);
        log.info("userPrincipal = " + userPrincipal);
        log.info("userPrincipal.getId() = " + userPrincipal.getId());
        log.info("userPrincipal.getEmail() = " + userPrincipal.getEmail());
        log.info("userPrincipal.getName() = " + userPrincipal.getName());
        log.info("userPrincipal.getUsername() = " + userPrincipal.getUsername());
//        userPrincipal.get
        String accessToken = Jwts.builder()
                .setSubject(Long.toString(userPrincipal.getId()))
                .setAudience(userPrincipal.getName())
                .setIssuedAt(new Date())
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        String refreshToken = Jwts.builder()
                .setExpiration(refreshTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        return TokenMapping.builder()
                .userEmail(userPrincipal.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(oAuth2Config.getAuth().getTokenSecret())
                .build()
                .parseClaimsJws(token)
                .getBody();
        log.info("토큰 subject = {}", claims.getAudience());
        log.info("토큰 subject = {}", claims.getSubject());
        return Long.parseLong(claims.getSubject());
    }

    public UsernamePasswordAuthenticationToken getAuthenticationById(String token){
        log.info("ID로 인증 찾기");
        Long userId = getUserIdFromToken(token);
        UserDetails userDetails = customUserDetailsService.loadUserById(userId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        return authentication;
    }

    public UsernamePasswordAuthenticationToken getAuthenticationByEmail(String email){
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        return authentication;
    }

    public Long getExpiration(String token) {
        // accessToken 남은 유효시간
        Date expiration = Jwts.parserBuilder().setSigningKey(oAuth2Config.getAuth().getTokenSecret()).build().parseClaimsJws(token).getBody().getExpiration();
        // 현재 시간
        Long now = new Date().getTime();
        //시간 계산
        return (expiration.getTime() - now);
    }

    public boolean validateToken(String token) {
        try {
            //log.info("bearerToken = {} \n oAuth2Config.getAuth()={}", token, oAuth2Config.getAuth().getTokenSecret());
            Jwts.parserBuilder().setSigningKey(oAuth2Config.getAuth().getTokenSecret()).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException ex) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (MalformedJwtException ex) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException ex) {
            log.error("만료된 JWT 토큰입니다.");
            throw new ExpiredTokenException(ErrorCode.EXPIRED_ACCESS_TOKEN);
        } catch (UnsupportedJwtException ex) {
            log.error("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException ex) {
            log.error("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    public boolean validateRefreshToken(String token) {
        try {
            //log.info("bearerToken = {} \n oAuth2Config.getAuth()={}", token, oAuth2Config.getAuth().getTokenSecret());
            Jwts.parserBuilder().setSigningKey(oAuth2Config.getAuth().getTokenSecret()).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException ex) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (MalformedJwtException ex) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException ex) {
            log.error("만료된 JWT 토큰입니다.");
            throw new ExpiredTokenException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        } catch (UnsupportedJwtException ex) {
            log.error("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException ex) {
            log.error("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }


}
