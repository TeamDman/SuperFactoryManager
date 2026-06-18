
#[cfg(feature = "tracy")]
pub fn set_tracy_thread_name(name: &str) {
    if let Some(client) = tracy_client::Client::running() {
        client.set_thread_name(name);
    }
}

#[cfg(not(feature = "tracy"))]
pub fn set_tracy_thread_name(_: &str) {}
