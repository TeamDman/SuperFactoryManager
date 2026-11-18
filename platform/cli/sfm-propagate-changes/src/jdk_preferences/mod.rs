//! Machine-local JDK registrations. A registration locates a JDK home, and
//! callers separately verify whether that runtime matches the selected lockfile.

mod jdk_preference;
mod jdk_preference_registry;

pub use jdk_preference::JdkPreference;
pub use jdk_preference_registry::JdkPreferenceRegistry;
