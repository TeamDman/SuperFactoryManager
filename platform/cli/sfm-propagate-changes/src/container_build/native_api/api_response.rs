use hyper::body;
use tokio::task;
pub(super) struct ApiResponse {
    pub response: hyper::Response<body::Incoming>,
    pub driver: task::JoinHandle<Result<(), hyper::Error>>,
}
impl Drop for ApiResponse {
    fn drop(&mut self) {
        self.driver.abort();
    }
}
