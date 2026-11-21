use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use argon2::{
    password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Algorithm, Argon2, Params, Version,
};
use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use bip39::{Language, Mnemonic};
use hkdf::Hkdf;
use hmac::Hmac;
use pbkdf2::pbkdf2;
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256, Sha512};
use sharks::{Share, Sharks};
use x25519_dalek::{PublicKey, StaticSecret};
use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AesGcmPayload {
    pub ciphertext_b64: String,
    pub iv_b64: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PinAuthRecord {
    pub phc_hash: String,
    pub kdf_salt_b64: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct X25519Keypair {
    pub private_b64: String,
    pub public_b64: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct RatchetState {
    pub root_key_b64: String,
    pub send_chain_key_b64: String,
    pub recv_chain_key_b64: String,
    pub send_count: u32,
    pub recv_count: u32,
    pub dh_self_priv_b64: String,
    pub dh_self_pub_b64: String,
    pub dh_remote_pub_b64: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RatchetEnvelope {
    pub dh_pub_b64: String,
    pub msg_num: u32,
    pub ciphertext_b64: String,
    pub iv_b64: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShamirShareBlock {
    pub index: u8,
    pub threshold: u8,
    pub total: u8,
    pub share_b64: String,
    pub checksum_hex: String,
}

pub fn random_bytes(len: usize) -> Vec<u8> {
    let mut buf = vec![0u8; len];
    OsRng.fill_bytes(&mut buf);
    buf
}

pub fn hash_pin_argon2(pin: &str) -> Result<PinAuthRecord, String> {
    let salt = SaltString::generate(&mut OsRng);
    let params = Params::new(16 * 1024, 2, 1, Some(32)).map_err(|e| e.to_string())?;
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    let phc = argon2
        .hash_password(pin.as_bytes(), &salt)
        .map_err(|e| e.to_string())?
        .to_string();

    let mut kdf_salt = [0u8; 16];
    OsRng.fill_bytes(&mut kdf_salt);

    Ok(PinAuthRecord {
        phc_hash: phc,
        kdf_salt_b64: B64.encode(kdf_salt),
    })
}

pub fn verify_pin_argon2(pin: &str, phc_hash: &str) -> bool {
    let parsed = match PasswordHash::new(phc_hash) {
        Ok(p) => p,
        Err(_) => return false,
    };
    Argon2::default()
        .verify_password(pin.as_bytes(), &parsed)
        .is_ok()
}

pub fn derive_key_pbkdf2(secret: &str, salt_b64: &str, iterations: u32) -> Result<Vec<u8>, String> {
    let salt = B64.decode(salt_b64).map_err(|e| e.to_string())?;
    let mut out = vec![0u8; 32];
    let iters = iterations.max(10_000);
    pbkdf2::<Hmac<Sha256>>(secret.as_bytes(), &salt, iters, &mut out)
        .map_err(|e| e.to_string())?;
    Ok(out)
}

pub fn encrypt_aes_gcm(plaintext: &[u8], key: &[u8]) -> Result<AesGcmPayload, String> {
    if key.len() != 32 {
        return Err(format!("AES-256-GCM requires 32-byte key, got {}", key.len()));
    }
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| e.to_string())?;
    let mut iv = [0u8; 12];
    OsRng.fill_bytes(&mut iv);
    let nonce = Nonce::from_slice(&iv);
    let ct = cipher.encrypt(nonce, plaintext).map_err(|e| e.to_string())?;
    let res = AesGcmPayload {
        ciphertext_b64: B64.encode(&ct),
        iv_b64: B64.encode(iv),
    };
    iv.zeroize();
    Ok(res)
}

pub fn decrypt_aes_gcm(ciphertext_b64: &str, iv_b64: &str, key: &[u8]) -> Result<Vec<u8>, String> {
    if key.len() != 32 {
        return Err(format!("AES-256-GCM requires 32-byte key, got {}", key.len()));
    }
    let ct = B64.decode(ciphertext_b64).map_err(|e| e.to_string())?;
    let mut iv = B64.decode(iv_b64).map_err(|e| e.to_string())?;
    if iv.len() != 12 {
        iv.zeroize();
        return Err("Invalid GCM IV length".to_string());
    }
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| e.to_string())?;
    let nonce = Nonce::from_slice(&iv);
    let pt = cipher.decrypt(nonce, ct.as_ref()).map_err(|e| e.to_string())?;
    iv.zeroize();
    Ok(pt)
}

pub fn generate_bip39_24() -> Result<Vec<String>, String> {
    let mut entropy = [0u8; 32];
    OsRng.fill_bytes(&mut entropy);
    let m = Mnemonic::from_entropy_in(Language::English, &entropy).map_err(|e| e.to_string())?;
    entropy.zeroize();
    Ok(m.words().map(|w| w.to_string()).collect())
}

pub fn generate_deterministic_bip39_24(date_str: &str, custom_words: &[String]) -> Result<Vec<String>, String> {
    let cleaned: Vec<String> = custom_words
        .iter()
        .map(|w| w.trim().to_lowercase())
        .filter(|w| !w.is_empty())
        .collect();
    let combined = format!("{}:{}", date_str.trim(), cleaned.join(" "));
    let mut hasher = Sha256::new();
    hasher.update(combined.as_bytes());
    let mut entropy: [u8; 32] = hasher.finalize().into();
    let m = Mnemonic::from_entropy_in(Language::English, &entropy).map_err(|e| e.to_string())?;
    entropy.zeroize();
    Ok(m.words().map(|w| w.to_string()).collect())
}

pub fn validate_bip39(words: &[String]) -> bool {
    let joined = words.join(" ");
    Mnemonic::parse_in(Language::English, &joined).is_ok()
}

pub fn bip39_to_seed_hex(words: &[String], passphrase: &str) -> Result<String, String> {
    let joined = words.join(" ");
    let m = Mnemonic::parse_in(Language::English, &joined).map_err(|e| e.to_string())?;
    let mut seed = m.to_seed(passphrase);
    let hex_out = hex::encode(seed);
    seed.zeroize();
    Ok(hex_out)
}

pub fn generate_x25519_keypair() -> X25519Keypair {
    let secret = StaticSecret::random_from_rng(OsRng);
    let public = PublicKey::from(&secret);
    let priv_bytes = secret.to_bytes();
    let kp = X25519Keypair {
        private_b64: B64.encode(priv_bytes),
        public_b64: B64.encode(public.as_bytes()),
    };
    kp
}

fn hkdf_derive_two(salt: &[u8], ikm: &[u8], info: &[u8]) -> ([u8; 32], [u8; 32]) {
    let hk = Hkdf::<Sha256>::new(Some(salt), ikm);
    let mut okm = [0u8; 64];
    let _ = hk.expand(info, &mut okm);
    let mut k1 = [0u8; 32];
    let mut k2 = [0u8; 32];
    k1.copy_from_slice(&okm[..32]);
    k2.copy_from_slice(&okm[32..64]);
    okm.zeroize();
    (k1, k2)
}

fn kdf_chain_step(chain_key: &[u8; 32]) -> ([u8; 32], [u8; 32]) {
    hkdf_derive_two(chain_key, b"MarcianoX-DoubleRatchet-ChainStep", b"msg-key")
}

pub fn ratchet_init(
    self_priv_b64: &str,
    self_pub_b64: &str,
    remote_pub_b64: &str,
    is_initiator: bool,
) -> Result<RatchetState, String> {
    let priv_vec = B64.decode(self_priv_b64).map_err(|e| e.to_string())?;
    let rem_vec = B64.decode(remote_pub_b64).map_err(|e| e.to_string())?;
    if priv_vec.len() != 32 || rem_vec.len() != 32 {
        return Err("X25519 keys must be 32 bytes".to_string());
    }
    let mut priv_arr = [0u8; 32];
    priv_arr.copy_from_slice(&priv_vec);
    let mut rem_arr = [0u8; 32];
    rem_arr.copy_from_slice(&rem_vec);

    let secret = StaticSecret::from(priv_arr);
    let remote = PublicKey::from(rem_arr);
    let shared = secret.diffie_hellman(&remote);

    let (root_key, dir_a) = hkdf_derive_two(b"MarcianoX-X3DH-Root", shared.as_bytes(), b"root-init");
    let (_, dir_b) = hkdf_derive_two(&root_key, shared.as_bytes(), b"chain-init");

    let (send_chain, recv_chain) = if is_initiator {
        (dir_a, dir_b)
    } else {
        (dir_b, dir_a)
    };

    priv_arr.zeroize();
    rem_arr.zeroize();

    Ok(RatchetState {
        root_key_b64: B64.encode(root_key),
        send_chain_key_b64: B64.encode(send_chain),
        recv_chain_key_b64: B64.encode(recv_chain),
        send_count: 0,
        recv_count: 0,
        dh_self_priv_b64: self_priv_b64.to_string(),
        dh_self_pub_b64: self_pub_b64.to_string(),
        dh_remote_pub_b64: remote_pub_b64.to_string(),
    })
}

pub fn ratchet_encrypt(state: &mut RatchetState, plaintext: &[u8]) -> Result<RatchetEnvelope, String> {
    let ck_vec = B64.decode(&state.send_chain_key_b64).map_err(|e| e.to_string())?;
    if ck_vec.len() != 32 {
        return Err("Invalid send chain key".to_string());
    }
    let mut ck = [0u8; 32];
    ck.copy_from_slice(&ck_vec);

    let (next_ck, mut msg_key) = kdf_chain_step(&ck);
    let enc = encrypt_aes_gcm(plaintext, &msg_key)?;
    let msg_num = state.send_count;
    state.send_count = state.send_count.saturating_add(1);
    state.send_chain_key_b64 = B64.encode(next_ck);

    ck.zeroize();
    msg_key.zeroize();

    Ok(RatchetEnvelope {
        dh_pub_b64: state.dh_self_pub_b64.clone(),
        msg_num,
        ciphertext_b64: enc.ciphertext_b64,
        iv_b64: enc.iv_b64,
    })
}

pub fn ratchet_decrypt(state: &mut RatchetState, envelope: &RatchetEnvelope) -> Result<Vec<u8>, String> {
    let ck_vec = B64.decode(&state.recv_chain_key_b64).map_err(|e| e.to_string())?;
    if ck_vec.len() != 32 {
        return Err("Invalid recv chain key".to_string());
    }
    let mut ck = [0u8; 32];
    ck.copy_from_slice(&ck_vec);

    // Advance chain up to envelope.msg_num if needed (capped at 1000 skips for DoS safety)
    let target = envelope.msg_num;
    if target < state.recv_count {
        ck.zeroize();
        return Err("Stale or replayed ratchet message number".to_string());
    }
    let skips = target - state.recv_count;
    if skips > 1000 {
        ck.zeroize();
        return Err("Ratchet skip limit exceeded".to_string());
    }

    let mut cur_ck = ck;
    let mut msg_key = [0u8; 32];
    for _ in 0..=skips {
        let (next_ck, mk) = kdf_chain_step(&cur_ck);
        cur_ck = next_ck;
        msg_key = mk;
    }

    let plaintext = decrypt_aes_gcm(&envelope.ciphertext_b64, &envelope.iv_b64, &msg_key)?;
    state.recv_chain_key_b64 = B64.encode(cur_ck);
    state.recv_count = target.saturating_add(1);

    ck.zeroize();
    cur_ck.zeroize();
    msg_key.zeroize();

    Ok(plaintext)
}

pub fn shamir_split_encrypted(
    payload: &[u8],
    passphrase: &str,
    threshold: u8,
    total: u8,
) -> Result<Vec<ShamirShareBlock>, String> {
    if threshold < 2 || total < threshold {
        return Err("Invalid Shamir threshold/total parameters".to_string());
    }
    // 1. Encrypt payload with fresh random 256-bit data key
    let mut data_key = [0u8; 32];
    OsRng.fill_bytes(&mut data_key);
    let enc_payload = encrypt_aes_gcm(payload, &data_key)?;

    // 2. Wrap data key + salt via PBKDF2-derived passphrase wrap key
    let mut wrap_salt = [0u8; 16];
    OsRng.fill_bytes(&mut wrap_salt);
    let wrap_salt_b64 = B64.encode(wrap_salt);
    let mut wrap_key = derive_key_pbkdf2(passphrase, &wrap_salt_b64, 100_000)?;

    let wrapped_secret = serde_json::json!({
        "k": B64.encode(data_key),
        "p": enc_payload,
    });
    let wrapped_bytes = serde_json::to_vec(&wrapped_secret).map_err(|e| e.to_string())?;
    let sealed_master = encrypt_aes_gcm(&wrapped_bytes, &wrap_key)?;

    // Combine wrap_salt_b64 + sealed_master into secret blob split by Shamir GF(256)
    let container_blob = serde_json::to_vec(&serde_json::json!({
        "s": wrap_salt_b64,
        "c": sealed_master
    }))
    .map_err(|e| e.to_string())?;

    let sharks = Sharks(threshold);
    let dealer = sharks.dealer(&container_blob);
    let shares: Vec<Share> = dealer.take(total as usize).collect();

    let mut out = Vec::with_capacity(shares.len());
    for (idx, share) in shares.into_iter().enumerate() {
        let share_bytes: Vec<u8> = Vec::from(&share);
        let mut hasher = Sha256::new();
        hasher.update(&share_bytes);
        let checksum_hex = hex::encode(hasher.finalize());
        out.push(ShamirShareBlock {
            index: (idx + 1) as u8,
            threshold,
            total,
            share_b64: B64.encode(&share_bytes),
            checksum_hex,
        });
    }

    data_key.zeroize();
    wrap_key.zeroize();
    Ok(out)
}

pub fn shamir_recover_encrypted(
    shares_b64: &[String],
    passphrase: &str,
) -> Result<Vec<u8>, String> {
    if shares_b64.len() < 2 {
        return Err("At least 2 Shamir shares required".to_string());
    }
    let mut parsed_shares = Vec::with_capacity(shares_b64.len());
    for s_b64 in shares_b64 {
        let raw = B64.decode(s_b64).map_err(|e| e.to_string())?;
        let share = Share::try_from(raw.as_slice()).map_err(|e| format!("{:?}", e))?;
        parsed_shares.push(share);
    }

    let sharks = Sharks(parsed_shares.len() as u8);
    let container_blob = sharks
        .recover(&parsed_shares)
        .map_err(|e| format!("Shamir recovery failed: {:?}", e))?;

    let outer: serde_json::Value =
        serde_json::from_slice(&container_blob).map_err(|e| e.to_string())?;
    let wrap_salt_b64 = outer["s"]
        .as_str()
        .ok_or_else(|| "Missing wrap salt".to_string())?;
    let sealed_master: AesGcmPayload =
        serde_json::from_value(outer["c"].clone()).map_err(|e| e.to_string())?;

    let mut wrap_key = derive_key_pbkdf2(passphrase, wrap_salt_b64, 100_000)?;
    let wrapped_bytes = decrypt_aes_gcm(
        &sealed_master.ciphertext_b64,
        &sealed_master.iv_b64,
        &wrap_key,
    )?;
    wrap_key.zeroize();

    let inner: serde_json::Value =
        serde_json::from_slice(&wrapped_bytes).map_err(|e| e.to_string())?;
    let data_key_b64 = inner["k"]
        .as_str()
        .ok_or_else(|| "Missing data key".to_string())?;
    let mut data_key = B64.decode(data_key_b64).map_err(|e| e.to_string())?;
    let enc_payload: AesGcmPayload =
        serde_json::from_value(inner["p"].clone()).map_err(|e| e.to_string())?;

    let pt = decrypt_aes_gcm(&enc_payload.ciphertext_b64, &enc_payload.iv_b64, &data_key)?;
    data_key.zeroize();
    Ok(pt)
}

#[allow(dead_code)]
fn _hmac_sha512(key: &[u8], data: &[u8]) -> Vec<u8> {
    use hmac::Mac;
    let mut mac = <Hmac<Sha512> as Mac>::new_from_slice(key).expect("HMAC key");
    mac.update(data);
    mac.finalize().into_bytes().to_vec()
}
