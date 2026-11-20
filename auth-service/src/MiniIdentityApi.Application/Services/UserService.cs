using MiniIdentityApi.Application.DTOs.Users;
using MiniIdentityApi.Application.Interfaces;

namespace MiniIdentityApi.Application.Services;

public class UserService
{
    private const string AdminRoleName = "Admin";

    private readonly IUserRepository _userRepository;
    private readonly IRoleRepository _roleRepository;

    public UserService(IUserRepository userRepository, IRoleRepository roleRepository)
    {
        _userRepository = userRepository;
        _roleRepository = roleRepository;
    }

    public List<UserResponse> GetAll() =>
        _userRepository.GetAll().Select(UserResponse.From).ToList();

    public UserResponse? GetById(Guid id)
    {
        var user = _userRepository.FindById(id);
        return user is null ? null : UserResponse.From(user);
    }

    public UserResponse Update(Guid id, string username, string email)
    {
        if (string.IsNullOrWhiteSpace(username))
            throw new ArgumentException("Username is required.");

        if (string.IsNullOrWhiteSpace(email))
            throw new ArgumentException("Email is required.");

        var user = _userRepository.FindById(id) ?? throw new KeyNotFoundException("User not found.");

        var conMismoNombre = _userRepository.FindByUsernameOrEmail(username.Trim());
        if (conMismoNombre is not null && conMismoNombre.Id != id)
            throw new InvalidOperationException("Username or email is already in use.");

        var conMismoCorreo = _userRepository.FindByUsernameOrEmail(email.Trim());
        if (conMismoCorreo is not null && conMismoCorreo.Id != id)
            throw new InvalidOperationException("Username or email is already in use.");

        user.UpdateProfile(username.Trim(), email.Trim());
        _userRepository.Save(user);

        return UserResponse.From(user);
    }

    public void Activate(Guid id)
    {
        var user = _userRepository.FindById(id) ?? throw new KeyNotFoundException("User not found.");
        user.Activate();
        _userRepository.Save(user);
    }

    public void Deactivate(Guid id)
    {
        var user = _userRepository.FindById(id) ?? throw new KeyNotFoundException("User not found.");
        user.Deactivate();
        _userRepository.Save(user);
    }

    public void Delete(Guid id)
    {
        var user = _userRepository.FindById(id) ?? throw new KeyNotFoundException("User not found.");

        if (EsAdmin(user) && !HayOtroAdmin(user.Id))
            throw new InvalidOperationException("No se puede eliminar el último administrador.");

        _userRepository.Delete(user);
    }

    public void AssignRole(Guid userId, string roleName)
    {
        var user = _userRepository.FindById(userId) ?? throw new KeyNotFoundException("User not found.");
        var role = _roleRepository.FindByName(roleName) ?? throw new KeyNotFoundException("Role not found.");

        user.AddRole(role);
        _userRepository.Save(user);
    }

    private static bool EsAdmin(Domain.Entities.User user) =>
        user.Roles.Any(r => r.Name.Equals(AdminRoleName, StringComparison.OrdinalIgnoreCase));

    private bool HayOtroAdmin(Guid userId) =>
        _userRepository.GetAll().Any(u =>
            u.Id != userId && EsAdmin(u));
}
