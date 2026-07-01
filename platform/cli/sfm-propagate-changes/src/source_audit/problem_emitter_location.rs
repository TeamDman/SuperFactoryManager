use std::fmt;
use std::panic::Location;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ProblemEmitterLocation {
    pub file: &'static str,
    pub line: u32,
    pub column: u32,
}

impl ProblemEmitterLocation {
    #[must_use]
    pub fn from_caller(location: &'static Location<'static>) -> Self {
        Self {
            file: location.file(),
            line: location.line(),
            column: location.column(),
        }
    }
}

impl fmt::Display for ProblemEmitterLocation {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}:{}:{}", self.file, self.line, self.column)
    }
}
