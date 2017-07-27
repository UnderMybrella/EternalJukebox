package org.abimon.eternalJukebox

import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT

fun JWTVerifier.verifySafely(token: String): DecodedJWT? = try { verify(token) } catch(verification: JWTVerificationException) { null }
fun JWTVerifier.isValid(token: String): Boolean = try { verify(token); true } catch(verification: JWTVerificationException) { false }