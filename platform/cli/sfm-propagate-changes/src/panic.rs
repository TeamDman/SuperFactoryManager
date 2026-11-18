use std::any;
pub fn panic_message(panic: &(dyn any::Any + Send)) -> String {
    if let Some(message) = panic.downcast_ref::<&str>() {
        return (*message).to_string();
    }
    if let Some(message) = panic.downcast_ref::<String>() {
        return message.clone();
    }
    "<non-string panic payload>".to_string()
}
