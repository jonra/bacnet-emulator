# Contributing to BACnet Emulator

Thank you for your interest in contributing to BACnet Emulator! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/yourusername/bacnet-emulator/issues)
2. If not, create a new issue with:
   - Clear, descriptive title
   - Steps to reproduce
   - Expected vs. actual behavior
   - Environment details (OS, Java version, etc.)
   - Relevant logs or error messages

### Suggesting Features

1. Check existing [Issues](https://github.com/yourusername/bacnet-emulator/issues) and [Discussions](https://github.com/yourusername/bacnet-emulator/discussions)
2. Create a new issue or discussion thread
3. Describe the feature and its use case
4. Explain why it would be valuable

### Submitting Code Changes

1. **Fork the Repository**
   ```bash
   git clone https://github.com/yourusername/bacnet-emulator.git
   cd bacnet-emulator
   ```

2. **Create a Branch**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

3. **Make Your Changes**
   - Follow the coding style guidelines below
   - Write or update tests
   - Update documentation as needed

4. **Test Your Changes**
   ```bash
   mvn clean test
   mvn spring-boot:run  # Test manually
   ```

5. **Commit Your Changes**
   ```bash
   git add .
   git commit -m "Add: description of your changes"
   ```
   
   Use clear, descriptive commit messages:
   - Start with a verb (Add, Fix, Update, Remove, etc.)
   - Keep the first line under 50 characters
   - Add more details in the body if needed

6. **Push to Your Fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your branch
   - Fill out the PR template
   - Submit the PR

## Coding Standards

### Java Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Keep methods focused and concise
- Add Javadoc comments for public APIs

### Code Formatting

- Use 4 spaces for indentation (not tabs)
- Maximum line length: 120 characters
- Use braces for all if/for/while statements
- Follow existing code style in the project

### Example

```java
/**
 * Retrieves a device by its instance ID.
 *
 * @param deviceInstanceId the device instance ID
 * @return the device DTO, or null if not found
 * @throws RuntimeException if device not found
 */
public DeviceDto getDeviceByInstanceId(Integer deviceInstanceId) {
    return deviceRepository.findByDeviceInstanceId(deviceInstanceId)
            .map(this::toDto)
            .orElseThrow(() -> new RuntimeException("Device not found: " + deviceInstanceId));
}
```

## Testing

- Write unit tests for new features
- Ensure all existing tests pass
- Test edge cases and error conditions
- Test with actual BACnet clients when possible

## Documentation

- Update README.md if adding new features
- Add Javadoc for new public methods
- Update API documentation if changing endpoints
- Include examples in documentation

## Pull Request Process

1. Ensure your code follows the coding standards
2. All tests must pass
3. Update documentation as needed
4. Request review from maintainers
5. Address any feedback
6. Once approved, maintainers will merge

## Development Setup

1. **Prerequisites**
   - JDK 17 or higher
   - Maven 3.6+
   - Git

2. **Clone and Build**
   ```bash
   git clone https://github.com/yourusername/bacnet-emulator.git
   cd bacnet-emulator
   mvn clean install
   ```

3. **Run Tests**
   ```bash
   mvn test
   ```

4. **Run Application**
   ```bash
   mvn spring-boot:run
   ```

5. **IDE Setup**
   - Import as Maven project
   - Install Lombok plugin
   - Configure code style (use project settings)

## Areas for Contribution

We welcome contributions in these areas:

- **New Features**: Additional BACnet object types, transport protocols, etc.
- **Bug Fixes**: Fix issues reported in GitHub Issues
- **Documentation**: Improve README, add examples, write tutorials
- **Testing**: Add unit tests, integration tests
- **Performance**: Optimize code, improve efficiency
- **UI/UX**: Improve web interface, add features
- **Examples**: Add more usage examples and tutorials

## Questions?

- Open a [Discussion](https://github.com/yourusername/bacnet-emulator/discussions)
- Check existing [Issues](https://github.com/yourusername/bacnet-emulator/issues)
- Review the [Wiki](https://github.com/yourusername/bacnet-emulator/wiki)

Thank you for contributing! 🎉

