use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::crypto::RatchetState;

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct ActiveVaultSession {
    pub active_alias: String,
    pub master_key: Vec<u8>,
}

struct VaultRuntimeState {
    session: Option<ActiveVaultSession>,
    ratchets: HashMap<i32, RatchetState>,
}

static VAULT_STATE: OnceLock<Mutex<VaultRuntimeState>> = OnceLock::new();

fn vault() -> &'static Mutex<VaultRuntimeState> {
    VAULT_STATE.get_or_init(|| {
        Mutex::new(VaultRuntimeState {
            session: None,
            ratchets: HashMap::new(),
        })
    })
}

pub fn set_active_session(alias: String, master_key: Vec<u8>) {
    if let Ok(mut guard) = vault().lock() {
        if let Some(mut prev) = guard.session.take() {
            prev.zeroize();
        }
        guard.session = Some(ActiveVaultSession {
            active_alias: alias,
            master_key,
        });
    }
}

pub fn get_active_master_key() -> Option<Vec<u8>> {
    vault()
        .lock()
        .ok()
        .and_then(|g| g.session.as_ref().map(|s| s.master_key.clone()))
}

pub fn set_ratchet_state(contact_id: i32, state: RatchetState) {
    if let Ok(mut guard) = vault().lock() {
        guard.ratchets.insert(contact_id, state);
    }
}

pub fn get_ratchet_state(contact_id: i32) -> Option<RatchetState> {
    vault()
        .lock()
        .ok()
        .and_then(|g| g.ratchets.get(&contact_id).cloned())
}

pub fn lock_and_zeroize_session() {
    if let Ok(mut guard) = vault().lock() {
        if let Some(mut s) = guard.session.take() {
            s.zeroize();
        }
        for (_, mut r) in guard.ratchets.drain() {
            r.zeroize();
        }
    }
}

pub fn panic_zeroize_all() -> usize {
    let mut wiped_items = 0;
    if let Ok(mut guard) = vault().lock() {
        if let Some(mut s) = guard.session.take() {
            s.zeroize();
            wiped_items += 1;
        }
        for (_, mut r) in guard.ratchets.drain() {
            r.zeroize();
            wiped_items += 1;
        }
    }
    wiped_items
}
