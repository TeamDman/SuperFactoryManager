//! Test-only observations; never admission authority or a success cache.
use std::cell::RefCell;

#[derive(Clone, Default, facet::Facet)]
pub(crate) struct Counts {
    pub sdk_rechecks: u64,
    pub hash_calls: u64,
    pub hashed_bytes: u64,
    pub input_capture_bytes: u64,
}
thread_local! { static COUNTS: RefCell<Counts> = RefCell::new(Counts::default()); }
pub(crate) fn reset() {
    COUNTS.with(|s| *s.borrow_mut() = Counts::default());
}
pub(crate) fn snapshot() -> Counts {
    COUNTS.with(|s| s.borrow().clone())
}
pub(crate) fn sdk() {
    COUNTS.with(|s| s.borrow_mut().sdk_rechecks += 1);
}
pub(crate) fn hash(bytes: u64) {
    COUNTS.with(|s| {
        let mut s = s.borrow_mut();
        s.hash_calls += 1;
        s.hashed_bytes += bytes;
    });
}
pub(crate) fn capture(bytes: u64) {
    COUNTS.with(|s| s.borrow_mut().input_capture_bytes += bytes);
}
