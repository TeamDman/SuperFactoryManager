use crate::paths::CACHE_DIR;

pub(super) fn invoke() {
    println!("{}", CACHE_DIR.0.display());
}
