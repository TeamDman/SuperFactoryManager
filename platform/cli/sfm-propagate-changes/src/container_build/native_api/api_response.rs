pub(super) struct ApiResponse {
    pub response: hyper::Response<hyper::body::Incoming>,
    pub driver: tokio::task::JoinHandle<Result<(), hyper::Error>>,
}
impl Drop for ApiResponse {
    fn drop(&mut self) {
        self.driver.abort();
    }
}
