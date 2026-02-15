use clap::Parser;

#[derive(Debug, Parser, Clone)]
pub struct GlobalArgs {
    /// Enable debug logging
    #[arg(long, global = true, default_value_t = false)]
    pub debug: bool,
    /// If false, the program will error when interaction is requested
    #[arg(long, global = true, default_value_t = true)]
    pub interactive: bool,
    /// If true, any confirmation prompt will be automatically approved
    #[arg(long, global = true, default_value_t = false)]
    pub auto_approve: bool,
}

impl Default for GlobalArgs {
    fn default() -> Self {
        Self {
            debug: false,
            interactive: true,
            auto_approve: false,
        }
    }
}
