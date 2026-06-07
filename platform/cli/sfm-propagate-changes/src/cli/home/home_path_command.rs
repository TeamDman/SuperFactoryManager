use crate::paths::APP_HOME;

pub(super) fn invoke() {
    println!("{}", APP_HOME.0.display());
}
