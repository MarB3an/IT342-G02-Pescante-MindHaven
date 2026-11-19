# MindHaven SRS v1.1 – Increment 1 Change Note

**Project:** MindHaven: Student Peer Support and Crisis Triage System  
**Course:** IT342-G02 – Systems Integration and Architecture 1  
**Author:** Merven Loui A. Pescante  
**Date:** November 19, 2026  

## Purpose
This change note records the intentional scope and authentication refinements made for **Project Increment 1 (User Accounts and Authentication)** relative to SRS v1.0.

## Summary of Changes
1. **Student Authentication Adapted to Email + Password (No OTP Verification):**
   - **SRS v1.0 (`FR-001`–`FR-004`):** Specified 6-digit email OTP verification via Twilio SendGrid with no student password.
   - **SRS v1.1 (Increment 1 Implementation):** Per project increment requirements (secure password hashing at registration) and developer confirmation, OTP verification via SendGrid is omitted in the implementation. Students self-register directly with an authorized campus email and a strong password (hashed with **BCrypt, strength 12**) and authenticate via email + password.
2. **Cryptographic Student Email Privacy Preserved (`NFR-SEC-001`):**
   - Instead of storing plaintext student email addresses, `student_account.email_hash` stores a deterministic `HMAC-SHA256(lowercase(email), EMAIL_HASH_PEPPER)` digest. This preserves zero-PII pseudonymity in `student_profile` while supporting exact login lookup and duplicate-account prevention (`409 Conflict`).
3. **Database Schema Updates (`V1__create_auth_tables.sql`):**
   - Replaced `otp_verification` with `student_account` (holding `email_hash`, `password_hash`, `failed_attempts`, `locked_until`).
   - Added `refresh_token` table (storing SHA-256 hashes of `HttpOnly` refresh cookies) to support true server-side session revocation upon logout.
   - Added `audit_log` table to fulfill `FR-038` immutable security audit logging.
4. **External Integrations Deferred to Later Increments:**
   - Topic chatroom WebSocket messaging (`FR-008`–`FR-014`), Google Gemini AI crisis triage (`FR-015`–`FR-020`), and Twilio SMS escalation (`FR-027`–`FR-030`) are scheduled for subsequent development increments.
