using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Enums;

namespace MiniIdentityApi.Application.Services;

public class AuthorizationService
{
    private readonly IUserRepository _userRepository;

    public AuthorizationService(IUserRepository userRepository)
    {
        _userRepository = userRepository;
    }

    public bool HasPermission(Guid userId, string permissionCode)
    {
        var user = _userRepository.FindById(userId);
        if (user is null || user.Status != UserStatus.Active)
            return false;

        return user.HasPermission(permissionCode);
    }
}