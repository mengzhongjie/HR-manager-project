/**
 * HR Manager System - Custom JavaScript
 */

(function () {
    'use strict';

    // ============================================
    // Toast Notification System
    // ============================================
    function showToast(message, type) {
        type = type || 'info';
        var container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }

        var bgMap = {
            success: 'bg-success',
            error: 'bg-danger',
            warning: 'bg-warning text-dark',
            info: 'bg-info text-dark'
        };

        var toast = document.createElement('div');
        toast.className = 'toast align-items-center text-white border-0 ' + (bgMap[type] || 'bg-info');
        toast.setAttribute('role', 'alert');
        toast.innerHTML =
            '<div class="d-flex">' +
            '  <div class="toast-body">' + message + '</div>' +
            '  <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>' +
            '</div>';

        container.appendChild(toast);
        var bsToast = new bootstrap.Toast(toast, { autohide: true, delay: 4000 });
        bsToast.show();

        // Auto-remove from DOM after hidden
        toast.addEventListener('hidden.bs.toast', function () {
            toast.remove();
        });
    }

    // Expose globally
    window.showToast = showToast;

    // ============================================
    // Confirm Dialog for Sensitive Actions
    // ============================================
    function confirmAction(message, callback) {
        if (typeof bootstrap === 'undefined') {
            if (confirm(message)) { callback(); }
            return;
        }

        var modalEl = document.getElementById('confirm-modal');
        if (!modalEl) {
            modalEl = document.createElement('div');
            modalEl.id = 'confirm-modal';
            modalEl.className = 'modal fade';
            modalEl.setAttribute('tabindex', '-1');
            modalEl.innerHTML =
                '<div class="modal-dialog modal-sm modal-dialog-centered">' +
                '  <div class="modal-content">' +
                '    <div class="modal-body"><p id="confirm-message"></p></div>' +
                '    <div class="modal-footer">' +
                '      <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>' +
                '      <button type="button" class="btn btn-danger" id="confirm-btn">确定</button>' +
                '    </div>' +
                '  </div>' +
                '</div>';
            document.body.appendChild(modalEl);
        }

        document.getElementById('confirm-message').textContent = message;
        var modal = new bootstrap.Modal(modalEl);
        var confirmed = false;

        document.getElementById('confirm-btn').onclick = function () {
            confirmed = true;
            modal.hide();
        };

        modalEl.addEventListener('hidden.bs.modal', function handler() {
            modalEl.removeEventListener('hidden.bs.modal', handler);
            if (confirmed) { callback(); }
        });

        modal.show();
    }
    window.confirmAction = confirmAction;

    // ============================================
    // Auto-attach confirm dialogs to forms with data-confirm
    // ============================================
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                var msg = form.getAttribute('data-confirm') || '确定执行此操作？';
                confirmAction(msg, function () {
                    form.submit();
                });
            });
        });

        // ============================================
        // File Upload Validation
        // ============================================
        var fileInput = document.querySelector('input[type="file"][accept=".pdf,application/pdf"]');
        if (fileInput) {
            fileInput.addEventListener('change', function () {
                var file = this.files[0];
                if (!file) return;

                var errorEl = document.getElementById('file-error');

                // Type check
                if (file.type !== 'application/pdf') {
                    showToast('请选择PDF格式的文件', 'error');
                    if (errorEl) errorEl.textContent = '请选择PDF格式的文件';
                    this.value = '';
                    return;
                }

                // Size check (10MB)
                var maxSize = 10 * 1024 * 1024;
                if (file.size > maxSize) {
                    var mb = (file.size / 1024 / 1024).toFixed(1);
                    showToast('文件过大 (' + mb + 'MB)，最大支持10MB', 'error');
                    if (errorEl) errorEl.textContent = '文件大小超过10MB限制';
                    this.value = '';
                    return;
                }

                if (errorEl) errorEl.textContent = '';
                showToast('已选择文件: ' + file.name, 'info');

                // Show upload progress
                var progressWrapper = document.getElementById('upload-progress');
                if (progressWrapper) {
                    progressWrapper.style.display = 'block';
                    progressWrapper.querySelector('.progress-bar').style.width = '0%';
                    progressWrapper.querySelector('.progress-text').textContent = '已选择文件，等待上传...';
                }
            });
        }

        // ============================================
        // Upload Progress Simulation
        // ============================================
        var uploadForm = document.querySelector('form[action*="/seeker/upload"]');
        if (uploadForm) {
            uploadForm.addEventListener('submit', function () {
                var progressWrapper = document.getElementById('upload-progress');
                if (!progressWrapper) {
                    // Create progress bar if it doesn't exist
                    progressWrapper = document.createElement('div');
                    progressWrapper.id = 'upload-progress';
                    progressWrapper.className = 'progress-wrapper';
                    progressWrapper.innerHTML =
                        '<div class="progress">' +
                        '  <div class="progress-bar progress-bar-striped progress-bar-animated" style="width: 0%">0%</div>' +
                        '</div>' +
                        '<div class="progress-text text-muted mt-1">正在上传...</div>';
                    var submitBtn = this.querySelector('button[type="submit"]');
                    if (submitBtn && submitBtn.parentNode) {
                        submitBtn.parentNode.appendChild(progressWrapper);
                    }
                }
                progressWrapper.style.display = 'block';
                var bar = progressWrapper.querySelector('.progress-bar');
                bar.style.width = '0%';
                bar.textContent = '0%';

                // Animate progress (just visual feedback, actual upload is synchronous)
                var progress = 0;
                var interval = setInterval(function () {
                    progress += Math.random() * 15;
                    if (progress > 90) progress = 90;
                    bar.style.width = progress + '%';
                    bar.textContent = Math.round(progress) + '%';
                }, 300);

                // Clear interval after form submits (page navigates away)
                setTimeout(function () { clearInterval(interval); }, 10000);
            });
        }

        // ============================================
        // Drag-and-Drop Upload Area
        // ============================================
        var uploadArea = document.querySelector('.upload-area');
        if (uploadArea) {
            var dropInput = uploadArea.querySelector('input[type="file"]');

            uploadArea.addEventListener('dragover', function (e) {
                e.preventDefault();
                this.classList.add('dragover');
            });

            uploadArea.addEventListener('dragleave', function () {
                this.classList.remove('dragover');
            });

            uploadArea.addEventListener('drop', function (e) {
                e.preventDefault();
                this.classList.remove('dragover');
                if (dropInput && e.dataTransfer.files.length > 0) {
                    dropInput.files = e.dataTransfer.files;
                    // Trigger change event for validation
                    var event = new Event('change', { bubbles: true });
                    dropInput.dispatchEvent(event);
                }
            });

            uploadArea.addEventListener('click', function () {
                if (dropInput) dropInput.click();
            });
        }

        // ============================================
        // Auto-hide Alerts after 5 seconds
        // ============================================
        document.querySelectorAll('.alert:not(.alert-permanent)').forEach(function (alert) {
            setTimeout(function () {
                alert.style.transition = 'opacity 0.5s';
                alert.style.opacity = '0';
                setTimeout(function () { alert.remove(); }, 500);
            }, 5000);
        });

        // ============================================
        // Active Navigation Highlight
        // ============================================
        var currentPath = window.location.pathname;
        document.querySelectorAll('.navbar-nav .nav-link').forEach(function (link) {
            var href = link.getAttribute('href');
            if (href && currentPath.indexOf(href) === 0 && href !== '/') {
                link.classList.add('active');
            }
            if (href && href !== '/' && currentPath === href) {
                link.classList.add('active');
            }
        });
    });

})();
