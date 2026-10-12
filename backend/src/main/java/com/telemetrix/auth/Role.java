package com.telemetrix.auth;

// Who may do what (docs/adr/0007); each role includes the ones below it: ADMIN > ANALYST > USER.
public enum Role {
	USER, ANALYST, ADMIN
}
